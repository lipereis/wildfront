package dev.wildfront.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.wildfront.config.ConfigManager;
import dev.wildfront.config.DiscoveryConfig;
import dev.wildfront.config.EventConfig;
import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.CacheSiteFinder;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.event.EventContext;
import dev.wildfront.event.PlayerCooldowns;
import dev.wildfront.event.WorldEvent;
import dev.wildfront.registry.WildfrontEvents;
import dev.wildfront.registry.WildfrontLootTables;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * In-world tests. They run on a real server, so they also prove that the loot table loads and
 * that the Minecraft and Fabric APIs behave the way the mod assumes.
 */
public class WildfrontGameTest {
	private static final long FAR_FUTURE = Long.MAX_VALUE / 2;
	private static final BlockPos CACHE = new BlockPos(1, 1, 1);

	private static boolean isTracked(ServerLevel level, BlockPos pos) {
		return DiscoveryManager.activeCaches(level).stream().map(ActiveCache::pos).anyMatch(pos::equals);
	}

	/**
	 * Describes what the site finder sees around a position, for failure messages.
	 */
	private static String describeSurface(ServerLevel level, BlockPos origin) {
		StringBuilder description = new StringBuilder("active caches: ").append(DiscoveryManager.activeCaches(level).size())
				.append(", dimension: ").append(level.dimension().identifier()).append(", surface relative to origin:");

		for (int dz = -3; dz <= 3; dz++) {
			description.append(" [");

			for (int dx = -3; dx <= 3; dx++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos site = new BlockPos(x, height, z);
				description.append(height - origin.getY()).append(CacheSiteFinder.isSuitable(level, site, origin) ? "+" : "-")
						.append(level.hasChunk(x >> 4, z >> 4) ? "" : "!").append(' ');
			}

			description.append(']');
		}

		return description.toString();
	}

	@GameTest
	public void cacheHasLootAndCountsAsFoundOnceOpened(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);
		long now = level.getGameTime();

		helper.assertTrue(DiscoveryManager.place(level, pos, FAR_FUTURE), "cache should be placed");
		helper.assertBlockPresent(Blocks.BARREL, CACHE);
		BarrelBlockEntity barrel = helper.getBlockEntity(CACHE, BarrelBlockEntity.class);
		helper.assertTrue(WildfrontLootTables.FORGOTTEN_CACHE.equals(barrel.getLootTable()), "cache should carry the forgotten cache loot table");
		helper.assertTrue(isTracked(level, pos), "placed cache should be tracked");

		DiscoveryManager.tick(level, now);
		helper.assertTrue(isTracked(level, pos), "unopened cache should stay tracked");
		helper.assertBlockPresent(Blocks.BARREL, CACHE);

		// What opening the barrel does on a real server.
		barrel.unpackLootTable(null);
		helper.assertTrue(barrel.getLootTable() == null, "opening should consume the loot table");
		helper.assertFalse(barrel.isEmpty(), "loot table should generate at least one item");

		DiscoveryManager.tick(level, now);
		helper.assertFalse(isTracked(level, pos), "opened cache should no longer be tracked");
		helper.assertBlockPresent(Blocks.BARREL, CACHE);
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest
	public void expiredCacheIsRemovedWithoutDroppingLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);
		long now = level.getGameTime();

		helper.assertTrue(DiscoveryManager.place(level, pos, now), "cache should be placed");
		DiscoveryManager.tick(level, now);

		helper.assertBlockPresent(Blocks.AIR, CACHE);
		helper.assertFalse(isTracked(level, pos), "expired cache should no longer be tracked");
		helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "removing an expired cache should not drop items");
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest
	public void cacheStaysUntilItsExpiryTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);
		long now = level.getGameTime();

		helper.assertTrue(DiscoveryManager.place(level, pos, now + 100), "cache should be placed");
		DiscoveryManager.tick(level, now + 99);
		helper.assertBlockPresent(Blocks.BARREL, CACHE);
		helper.assertTrue(isTracked(level, pos), "cache should stay until its expiry time");

		DiscoveryManager.tick(level, now + 100);
		helper.assertBlockPresent(Blocks.AIR, CACHE);
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	/**
	 * Grass under a cache turns to dirt in a real world. The original ground must come back when
	 * an unopened cache is removed, and nothing else may be touched.
	 */
	@GameTest
	public void groundUnderAnExpiredCacheIsRestored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		BlockPos[] spots = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(1, 1, 5), new BlockPos(3, 1, 5)};
		Block[] grounds = {Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK};

		for (int i = 0; i < spots.length; i++) {
			helper.setBlock(spots[i].below(), grounds[i]);
			helper.assertTrue(DiscoveryManager.place(level, helper.absolutePos(spots[i]), now), "cache " + i + " should be placed");
		}

		// What the game does to grass and mycelium under a solid block, and a player replacing
		// the ground under the last cache with something else.
		helper.setBlock(spots[0].below(), Blocks.DIRT);
		helper.setBlock(spots[1].below(), Blocks.DIRT);
		helper.setBlock(spots[4].below(), Blocks.COBBLESTONE);

		DiscoveryManager.tick(level, now);

		for (BlockPos spot : spots) {
			helper.assertBlockPresent(Blocks.AIR, spot);
		}

		helper.assertBlockPresent(Blocks.GRASS_BLOCK, spots[0].below());
		helper.assertBlockPresent(Blocks.MYCELIUM, spots[1].below());
		helper.assertBlockPresent(Blocks.STONE, spots[2].below());
		helper.assertBlockPresent(Blocks.DIRT, spots[3].below());
		helper.assertBlockPresent(Blocks.COBBLESTONE, spots[4].below());
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest
	public void groundIsNotRestoredUnderSomethingBuiltWhereTheCacheStood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);

		helper.setBlock(CACHE.below(), Blocks.GRASS_BLOCK);
		helper.assertTrue(DiscoveryManager.place(level, pos, FAR_FUTURE), "cache should be placed");
		helper.setBlock(CACHE.below(), Blocks.DIRT);
		helper.setBlock(CACHE, Blocks.OAK_PLANKS);
		DiscoveryManager.tick(level, level.getGameTime());

		helper.assertFalse(isTracked(level, pos), "replaced cache should no longer be tracked");
		helper.assertBlockPresent(Blocks.DIRT, CACHE.below());

		helper.setBlock(CACHE, Blocks.AIR);
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest
	public void groundUnderABrokenCacheIsRestored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);

		helper.setBlock(CACHE.below(), Blocks.GRASS_BLOCK);
		helper.assertTrue(DiscoveryManager.place(level, pos, FAR_FUTURE), "cache should be placed");
		helper.setBlock(CACHE.below(), Blocks.DIRT);
		helper.setBlock(CACHE, Blocks.AIR);
		DiscoveryManager.tick(level, level.getGameTime());

		helper.assertBlockPresent(Blocks.GRASS_BLOCK, CACHE.below());
		helper.killAllEntities();
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	/**
	 * Worlds saved before the ground was recorded contain caches without it; they must still load.
	 */
	@GameTest
	public void cachesSavedWithoutGroundStillLoad(GameTestHelper helper) {
		ActiveCache old = ActiveCache.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"pos\": [1, 2, 3], \"expires_at\": 5}"))
				.result().orElse(null);
		helper.assertTrue(old != null, "a cache saved without ground should load");
		helper.assertTrue(old.pos().equals(new BlockPos(1, 2, 3)) && old.expiresAt() == 5 && old.ground().isEmpty(), "unexpected result: " + old);

		ActiveCache current = new ActiveCache(new BlockPos(4, 5, 6), 7, Optional.of(Blocks.GRASS_BLOCK.defaultBlockState()));
		JsonElement saved = ActiveCache.CODEC.encodeStart(JsonOps.INSTANCE, current).result().orElseThrow();
		helper.assertTrue(current.equals(ActiveCache.CODEC.parse(JsonOps.INSTANCE, saved).result().orElse(null)), "a cache should survive saving and loading: " + saved);
		helper.succeed();
	}

	@GameTest
	public void brokenCacheIsForgotten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);

		helper.assertTrue(DiscoveryManager.place(level, pos, FAR_FUTURE), "cache should be placed");
		helper.setBlock(CACHE, Blocks.AIR);
		DiscoveryManager.tick(level, level.getGameTime());

		helper.assertFalse(isTracked(level, pos), "destroyed cache should no longer be tracked");
		helper.killAllEntities();
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest
	public void expiredCachesDoNotBlockNewOnes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CACHE);
		long now = level.getGameTime();
		long active = DiscoveryManager.activeCaches(level).stream().filter(cache -> cache.expiresAt() > now).count();

		helper.assertTrue(DiscoveryManager.place(level, pos, now + 100), "cache should be placed");
		helper.assertFalse(DiscoveryManager.hasCapacity(level, now, (int) active + 1), "an unexpired cache should use capacity");
		helper.assertTrue(DiscoveryManager.hasCapacity(level, now + 100, (int) active + 1), "an expired cache should not use capacity");

		DiscoveryManager.tick(level, now + 100);
		GameTestSupport.removeCaches(helper);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void siteFinderOnlyAcceptsFreeSpaceOnSolidGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameTestSupport.buildFloor(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));

		for (int i = 0; i < 20; i++) {
			Optional<BlockPos> site = CacheSiteFinder.find(level, origin, 1, 3, level.getRandom());
			helper.assertTrue(site.isPresent(), "a site should be found on a flat stone floor");
			helper.assertTrue(site.get().getY() == origin.getY(), "site should be directly above the floor but was " + site.get());
			helper.assertTrue(level.getBlockState(site.get().below()).is(Blocks.STONE), "site should stand on the floor");
			helper.assertTrue(site.get().closerThan(origin, 4.5), "site should be within the requested distance but was " + site.get());
		}

		helper.assertTrue(CacheSiteFinder.isSuitable(level, origin, origin), "free space on stone should be suitable");

		helper.setBlock(3, 1, 3, Blocks.OAK_PLANKS);
		helper.assertFalse(CacheSiteFinder.isSuitable(level, origin, origin), "an occupied space should not be suitable");

		helper.setBlock(3, 1, 3, Blocks.SHORT_GRASS);
		helper.assertTrue(CacheSiteFinder.isSuitable(level, origin, origin), "replaceable plants may be replaced");

		helper.setBlock(3, 1, 3, Blocks.AIR);
		helper.setBlock(3, 0, 3, Blocks.AIR);
		helper.assertFalse(CacheSiteFinder.isSuitable(level, origin, origin), "a space without ground should not be suitable");

		helper.setBlock(3, 0, 3, Blocks.OAK_LEAVES);
		helper.assertFalse(CacheSiteFinder.isSuitable(level, origin, origin), "leaves should not count as ground");

		helper.assertFalse(CacheSiteFinder.isSuitable(level, origin.above(40), origin), "a site far above the player should not be suitable");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void mysteriousDiscoveryPlacesACacheNearThePlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameTestSupport.buildFloor(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);

		try {
			WorldEvent event = WildfrontEvents.MYSTERIOUS_DISCOVERY;
			EventConfig eventConfig = event.defaultConfig();
			WildfrontConfig config = new WildfrontConfig(true, 10, 120, 48, Map.of(event.id(), eventConfig), new DiscoveryConfig(1, 3, 60, 64));
			long now = level.getGameTime();

			Optional<WorldEvent.Plan> plan = event.plan(new EventContext(player, level, now, eventConfig, config));
			helper.assertTrue(plan.isPresent(), "the event should find a place on a flat stone floor; " + describeSurface(level, origin));
			helper.assertTrue(DiscoveryManager.activeCaches(level).stream().noneMatch(cache -> cache.pos().closerThan(origin, 6)),
					"planning must not change the world");

			plan.get().run(List.of(player));

			List<ActiveCache> placed = DiscoveryManager.activeCaches(level).stream().filter(cache -> cache.pos().closerThan(origin, 6)).toList();
			helper.assertTrue(placed.size() == 1, "exactly one cache should appear but found " + placed.size());
			helper.assertTrue(level.getBlockState(placed.get(0).pos()).is(Blocks.BARREL), "the cache should be a barrel");
			helper.assertTrue(placed.get(0).expiresAt() == now + 60 * 20, "the cache should expire after the configured lifetime");

			// The automatic loop must leave the cache alone until it expires or is opened.
			DiscoveryManager.tick(level, now);
			helper.assertTrue(isTracked(level, placed.get(0).pos()), "a fresh cache should stay tracked");
		} finally {
			GameTestSupport.remove(helper, player);
			GameTestSupport.removeCaches(helper);
		}

		helper.succeed();
	}

	@GameTest
	public void cooldownsAreStoredOnThePlayer(GameTestHelper helper) {
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);

		try {
			PlayerCooldowns cooldowns = new PlayerCooldowns(player);
			helper.assertTrue(cooldowns.lastTrigger("some_event").isEmpty(), "a new player should have no cooldowns");

			cooldowns.recordTrigger("some_event", 1234);
			cooldowns.recordTrigger("other_event", 99);
			helper.assertTrue(new PlayerCooldowns(player).lastTrigger("some_event").equals(OptionalLong.of(1234)), "cooldown should be read back");
			helper.assertTrue(new PlayerCooldowns(player).lastTrigger("other_event").equals(OptionalLong.of(99)), "cooldowns should not overwrite each other");

			cooldowns.clear();
			helper.assertTrue(cooldowns.lastTrigger("some_event").isEmpty(), "cleared cooldowns should be gone");
		} finally {
			GameTestSupport.remove(helper, player);
		}

		helper.succeed();
	}

	@GameTest
	public void commandsAndConfigAreAvailableOnTheServer(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("wildfront") != null,
				"/wildfront should be registered");
		helper.assertTrue(ConfigManager.get().events().containsKey(WildfrontEvents.MYSTERIOUS_DISCOVERY.id()),
				"the loaded config should contain every registered event");
		helper.succeed();
	}
}
