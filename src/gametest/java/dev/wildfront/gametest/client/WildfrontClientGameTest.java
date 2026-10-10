package dev.wildfront.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;

import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;
import dev.wildfront.worldgen.HeightSpread;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Plays through the mod in a real client with a normally generated world: finds a naturally
 * generated abandoned camp, triggers the Mysterious Discovery event by command and lets it trigger
 * on its own. Screenshots of each step are written to {@code build/run/clientGameTest/screenshots}
 * for a human to look at.
 *
 * <p>Run with {@code gradlew runClientGameTest}. It opens a game window, so it is not part of
 * {@code build}.
 */
public class WildfrontClientGameTest implements FabricClientGameTest {
	private static final String SEED = "wildfront";
	private static final int SEARCH_RADIUS_CHUNKS = 100;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> {
					settings.setSeed(SEED);
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					settings.setAllowCommands(true);
				})
				.create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			String player = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getGameProfile().name());
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");

			naturallyGeneratedCamp(context, world, player);
			layoutGallery(context, world, player);
			forcedEvent(context, world, player);
			automaticEvent(context, world, player);
		}
	}

	private static void naturallyGeneratedCamp(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();

		BlockPos located = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Holder<Structure> camp = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.ABANDONED_CAMP).orElseThrow();
			Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
					level, HolderSet.direct(camp), s.getPlayerList().getPlayers().get(0).blockPosition(), SEARCH_RADIUS_CHUNKS, false);
			return nearest == null ? null : nearest.getFirst();
		});
		check(located != null, "world generation should place an abandoned camp within " + SEARCH_RADIUS_CHUNKS + " chunks of spawn");

		server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
		world.getConnection().waitForChunksRender();

		BlockPos campfire = server.computeOnServer(s -> findCampfire(s.overworld(), located));
		check(campfire != null, "the camp located at " + located.toShortString() + " should have generated its campfire");

		boolean hasLootChest = server.computeOnServer(s -> BlockPos.betweenClosedStream(campfire.offset(-6, -1, -6), campfire.offset(6, 2, 6))
				.anyMatch(pos -> s.overworld().getBlockEntity(pos) instanceof ChestBlockEntity chest
						&& WildfrontLootTables.ABANDONED_CAMP.equals(chest.getLootTable())));
		check(hasLootChest, "the generated camp should contain its loot chest");

		int offGround = server.computeOnServer(s -> heightAboveGround(s.overworld(), campfire));
		check(Math.abs(offGround) <= 1, "the camp should stand at ground level, but its campfire is " + offGround + " block(s) off");
		int spread = server.computeOnServer(s -> terrainSpread(s.overworld(), located));
		check(spread <= 3, "camps should only generate on roughly level ground, but the terrain around this one varies by " + spread + " blocks");
		System.out.println("[WildfrontClientGameTest] natural abandoned camp at " + campfire.toShortString() + ", terrain varies by " + spread + " block(s), campfire " + offGround + " block(s) off ground level");
		lookAt(context, world, player, campfire);
		takeWorldScreenshot(context, "1_abandoned_camp");
		server.runCommand("tp " + player + " " + (campfire.getX() + 7.5) + " " + (campfire.getY() + 4) + " " + (campfire.getZ() + 7.5) + " 135 25");
		context.waitTicks(20);
		takeWorldScreenshot(context, "1_abandoned_camp_from_south_east");
	}

	/**
	 * Builds every camp layout on a dry, level, open patch of the terrain and takes a picture of
	 * each, so a change to a layout can be looked at without hunting for it in the world.
	 */
	private static void layoutGallery(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		List<BlockPos> sites = server.computeOnServer(s -> findGallerySites(s.overworld(),
				s.getPlayerList().getPlayers().get(0).blockPosition(), WildfrontStructures.ABANDONED_CAMP_LAYOUTS.size()));
		check(sites.size() == WildfrontStructures.ABANDONED_CAMP_LAYOUTS.size(),
				"expected to find a level patch of ground for every layout near the camp, but found " + sites.size());

		for (int i = 0; i < sites.size(); i++) {
			Identifier layout = WildfrontStructures.ABANDONED_CAMP_LAYOUTS.get(i);
			BlockPos site = sites.get(i);
			String name = layout.getPath().substring(layout.getPath().lastIndexOf('/') + 1);

			// One below the surface, so the layout's ground layer replaces the top block.
			server.runCommand("place template " + layout + " " + site.getX() + " " + (site.getY() - 1) + " " + site.getZ());
			check(server.computeOnServer(s -> BlockPos.betweenClosedStream(site.below(), site.offset(6, 2, 6))
					.anyMatch(pos -> s.overworld().getBlockState(pos).is(Blocks.CAMPFIRE))), "layout " + layout + " should have been built at " + site.toShortString());

			// From the south-east, which is the side the tent and the ruin are open to.
			server.runCommand("gamemode spectator " + player);
			server.runCommand("tp " + player + " " + (site.getX() + 9.5) + " " + (site.getY() + 6) + " " + (site.getZ() + 9.5) + " 135 38");
			world.getConnection().waitForChunksRender();
			takeWorldScreenshot(context, "1_layout_" + name);
		}
	}

	/**
	 * Finds north-west corners of 7x7 patches near {@code center} that are level, dry and not under
	 * trees, at least 12 blocks apart. Only looks at loaded chunks.
	 */
	private static List<BlockPos> findGallerySites(ServerLevel level, BlockPos center, int count) {
		List<BlockPos> sites = new ArrayList<>();

		for (int ring = 1; ring <= 7 && sites.size() < count; ring++) {
			for (int dx = -ring; dx <= ring && sites.size() < count; dx++) {
				for (int dz = -ring; dz <= ring && sites.size() < count; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					int x = center.getX() + dx * 12;
					int z = center.getZ() + dz * 12;

					if (!level.isLoaded(new BlockPos(x, center.getY(), z)) || !level.isLoaded(new BlockPos(x + 6, center.getY(), z + 6))) {
						continue;
					}

					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					boolean suitable = true;

					for (int ox = 0; ox <= 6 && suitable; ox += 3) {
						for (int oz = 0; oz <= 6 && suitable; oz += 3) {
							BlockPos ground = new BlockPos(x + ox, y - 1, z + oz);
							suitable = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + ox, z + oz) == y
									&& level.getHeight(Heightmap.Types.WORLD_SURFACE, x + ox, z + oz) <= y + 1
									&& level.getBlockState(ground).is(Blocks.GRASS_BLOCK)
									&& level.getFluidState(ground.above()).isEmpty();
						}
					}

					if (suitable) {
						sites.add(new BlockPos(x, y, z));
					}
				}
			}
		}

		return sites;
	}

	private static void forcedEvent(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		int before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());

		server.runCommand("wildfront event trigger mysterious_discovery " + player);
		context.waitTicks(5);
		context.takeScreenshot("2_event_message");

		List<ActiveCache> caches = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()));
		check(caches.size() == before + 1, "the trigger command should place exactly one cache, but active caches went from " + before + " to " + caches.size());
		BlockPos cache = caches.get(caches.size() - 1).pos();
		check(server.computeOnServer(s -> s.overworld().getBlockState(cache).is(Blocks.BARREL)), "the cache should be a barrel");

		System.out.println("[WildfrontClientGameTest] forced cache at " + cache.toShortString());
		lookAt(context, world, player, cache);
		// Particles appear on the cache's 2-second cycle; wait for a fresh burst before the picture.
		context.waitTicks(45);
		takeWorldScreenshot(context, "3_forgotten_cache");
	}

	private static void automaticEvent(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		Path config = FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.FILE_NAME);

		try {
			Files.writeString(config, """
					{
						"checkIntervalSeconds": 1,
						"globalCooldownSeconds": 0,
						"events": {
							"mysterious_discovery": { "cooldownSeconds": 600, "triggerChance": 1, "minDistanceFromSpawn": 0 }
						}
					}
					""");
			server.runCommand("wildfront reload");
			server.runCommand("wildfront event reset " + player);
			server.runCommand("gamemode survival " + player);
			int before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());

			// One check per second and a certain trigger: the event must fire within a few seconds.
			server.waitFor(s -> DiscoveryManager.activeCaches(s.overworld()).size() > before, 200);
			context.waitTicks(10);
			context.takeScreenshot("4_automatic_event");

			// Ten more checks during the 600-second cooldown must not trigger anything.
			context.waitTicks(200);
			int after = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());
			check(after == before + 1, "the cooldown should allow exactly one automatic event, but active caches went from " + before + " to " + after);
		} catch (IOException e) {
			throw new AssertionError("could not write the test config", e);
		} finally {
			try {
				Files.deleteIfExists(config);
			} catch (IOException e) {
				System.out.println("[WildfrontClientGameTest] could not delete " + config + ": " + e);
			}

			server.runCommand("wildfront reload");
		}
	}

	/**
	 * Difference between the highest and lowest terrain height the generator reports around the
	 * camp's start, sampled the way the camp's structure type samples it.
	 */
	static int terrainSpread(ServerLevel level, BlockPos located) {
		int[][] offsets = HeightSpread.sampleOffsets(7);
		int[] heights = new int[offsets.length];

		for (int i = 0; i < offsets.length; i++) {
			heights[i] = level.getChunkSource().getGenerator().getFirstOccupiedHeight((located.getX() & ~15) + offsets[i][0],
					(located.getZ() & ~15) + offsets[i][1], Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
		}

		return HeightSpread.spread(heights);
	}

	/**
	 * How far above (positive) or below (negative) the terrain's own ground level the campfire
	 * stands. A camp sunk into a dip or perched on a mound gives a value away from 0.
	 */
	static int heightAboveGround(ServerLevel level, BlockPos campfire) {
		return campfire.getY() - level.getChunkSource().getGenerator().getFirstFreeHeight(campfire.getX(), campfire.getZ(),
				Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
	}

	static BlockPos findCampfire(ServerLevel level, BlockPos near) {
		for (BlockPos pos : BlockPos.betweenClosed(near.getX() - 16, level.getMinY(), near.getZ() - 16, near.getX() + 16, level.getMaxY(), near.getZ() + 16)) {
			if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/**
	 * Hovers the player above and to the north-west of the target, looking down at it. Spectator
	 * mode keeps the camera in place; in any other mode the player would fall before the picture.
	 */
	private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, String player, BlockPos target) {
		world.getServer().runCommand("gamemode spectator " + player);
		world.getServer().runCommand("tp " + player + " " + (target.getX() - 6.5) + " " + (target.getY() + 8) + " " + (target.getZ() - 6.5) + " -45 40");
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
	}

	/**
	 * Takes a picture once the chat lines from earlier commands have faded (they stay 10 seconds).
	 */
	private static void takeWorldScreenshot(ClientGameTestContext context, String name) {
		context.waitTicks(220);
		context.takeScreenshot(name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
