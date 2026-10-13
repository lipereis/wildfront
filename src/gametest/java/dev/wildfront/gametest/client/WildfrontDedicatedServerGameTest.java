package dev.wildfront.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import com.mojang.datafixers.util.Pair;

import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.entity.GuardEntity;
import dev.wildfront.event.PlayerCooldowns;
import dev.wildfront.registry.WildfrontEvents;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Plays through the mod on a real dedicated server with a real client connected to it: command
 * permissions, a forced and an automatic event, finding and expiry of a cache, a naturally
 * generated abandoned camp, and guards as the client sees them.
 *
 * <p>Starting a dedicated server requires accepting the Minecraft EULA
 * (https://aka.ms/MinecraftEULA). The test only runs when the build was started with
 * {@code -Pwildfront.acceptMinecraftEula=true}; otherwise it reports that it was skipped.
 */
public class WildfrontDedicatedServerGameTest implements FabricClientGameTest {
	private static final String EVENT = WildfrontEvents.MYSTERIOUS_DISCOVERY.id();
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	private static final int CLIENT_SYNC_TIMEOUT_TICKS = 600;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!isEulaAccepted()) {
			System.out.println("[WildfrontDedicatedServerGameTest] SKIPPED: the Minecraft EULA was not accepted for this run "
					+ "(start the build with -Pwildfront.acceptMinecraftEula=true to run it)");
			return;
		}

		Properties properties = new Properties();
		properties.setProperty("level-seed", "wildfront");

		// Without "consistent settings" the framework generates normal terrain instead of a flat
		// world; camps and realistic cache placement need that.
		try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(false).createServer(properties);
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			check(server.computeOnServer(s -> s.isDedicatedServer()), "the server should be a dedicated server");
			String player = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getGameProfile().name());
			System.out.println("[WildfrontDedicatedServerGameTest] connected as " + player);

			check(server.computeOnServer(s -> ConfigManager.get().events().containsKey(EVENT)), "the server should have loaded its config");
			permissions(server, player);
			forcedEventAndFinding(context, server, connection, player);
			automaticEventAndExpiry(context, server, player);
			naturallyGeneratedCamp(context, server, connection, player);
			guardsOverTheNetwork(context, server, connection, player);
			System.out.println("[WildfrontDedicatedServerGameTest] PASSED");
		}
	}

	private static boolean isEulaAccepted() {
		try {
			Path eula = Path.of("eula.txt");
			return Files.exists(eula) && Files.readString(eula).lines().anyMatch(line -> line.trim().equalsIgnoreCase("eula=true"));
		} catch (IOException e) {
			return false;
		}
	}

	private static void permissions(TestDedicatedServerContext server, String player) {
		check(!server.computeOnServer(s -> s.getCommands().getDispatcher().getRoot().getChild("wildfront")
				.canUse(s.getPlayerList().getPlayerByName(player).createCommandSourceStack())),
				"a player who is not an operator must not be able to use /wildfront");

		server.runCommand("op " + player);
		check(server.computeOnServer(s -> s.getCommands().getDispatcher().getRoot().getChild("wildfront")
				.canUse(s.getPlayerList().getPlayerByName(player).createCommandSourceStack())),
				"an operator should be able to use /wildfront");
	}

	private static void forcedEventAndFinding(ClientGameTestContext context, TestDedicatedServerContext server,
			TestDedicatedServerConnection connection, String player) {
		int before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());
		server.runCommand("wildfront event trigger mysterious_discovery " + player);
		context.waitTicks(10);

		List<ActiveCache> caches = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()));
		check(caches.size() == before + 1, "the trigger command should place exactly one cache, but active caches went from " + before + " to " + caches.size());
		BlockPos cache = caches.get(caches.size() - 1).pos();
		System.out.println("[WildfrontDedicatedServerGameTest] forced cache at " + cache.toShortString());

		check(server.computeOnServer(s -> new PlayerCooldowns(s.getPlayerList().getPlayerByName(player)).lastTrigger(EVENT).isPresent()),
				"the player's cooldown should be stored on the server");

		// The client must see the block the server placed.
		// Fails with a timeout if the client never sees the barrel.
		context.waitFor(client -> client.level != null && client.level.getBlockState(cache).is(Blocks.BARREL), CLIENT_SYNC_TIMEOUT_TICKS);
		context.takeScreenshot("5_dedicated_server_event");

		// What opening the barrel does; the server's own timer must then notice it was found.
		server.runOnServer(s -> {
			if (s.overworld().getBlockEntity(cache) instanceof BarrelBlockEntity barrel) {
				barrel.unpackLootTable(s.getPlayerList().getPlayerByName(player));
			}
		});
		server.waitFor(s -> DiscoveryManager.activeCaches(s.overworld()).stream().noneMatch(active -> active.pos().equals(cache)), 100);
		check(server.computeOnServer(s -> s.overworld().getBlockEntity(cache) instanceof BarrelBlockEntity barrel && !barrel.isEmpty()),
				"the opened cache should stay and contain loot");
	}

	private static void automaticEventAndExpiry(ClientGameTestContext context, TestDedicatedServerContext server, String player) {
		Path config = FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.FILE_NAME);
		String original;

		try {
			original = Files.readString(config);
			Files.writeString(config, """
					{
						"checkIntervalSeconds": 1,
						"globalCooldownSeconds": 0,
						"events": {
							"mysterious_discovery": { "cooldownSeconds": 600, "triggerChance": 1, "minDistanceFromSpawn": 0 }
						},
						"discovery": { "lifetimeSeconds": 30 }
					}
					""");
		} catch (IOException e) {
			throw new AssertionError("could not write the test config", e);
		}

		try {
			server.runCommand("wildfront reload");
			server.runCommand("wildfront event reset " + player);
			server.runCommand("gamemode survival " + player);
			List<BlockPos> before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).stream().map(ActiveCache::pos).toList());

			server.waitFor(s -> DiscoveryManager.activeCaches(s.overworld()).size() > before.size(), 200);
			BlockPos cache = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).stream()
					.map(ActiveCache::pos).filter(pos -> !before.contains(pos)).findFirst().orElseThrow());
			System.out.println("[WildfrontDedicatedServerGameTest] automatic cache at " + cache.toShortString());

			// Ten more checks during the 600-second cooldown must not trigger anything.
			context.waitTicks(200);
			int during = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());
			check(during == before.size() + 1, "the cooldown should allow exactly one automatic event, but active caches went from " + before.size() + " to " + during);

			// Unopened, the cache is removed 30 seconds (600 ticks) after it appeared.
			server.waitFor(s -> DiscoveryManager.activeCaches(s.overworld()).stream().noneMatch(active -> active.pos().equals(cache)), 600);
			check(server.computeOnServer(s -> s.overworld().getBlockState(cache).isAir()), "an expired cache should be removed from the world");
		} finally {
			try {
				Files.writeString(config, original);
			} catch (IOException e) {
				System.out.println("[WildfrontDedicatedServerGameTest] could not restore " + config + ": " + e);
			}

			server.runCommand("wildfront reload");
		}
	}

	private static void naturallyGeneratedCamp(ClientGameTestContext context, TestDedicatedServerContext server,
			TestDedicatedServerConnection connection, String player) {
		BlockPos located = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Holder<Structure> camp = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.ABANDONED_CAMP).orElseThrow();
			Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
					level, HolderSet.direct(camp), s.getPlayerList().getPlayerByName(player).blockPosition(), SEARCH_RADIUS_CHUNKS, false);
			return nearest == null ? null : nearest.getFirst();
		});
		check(located != null, "world generation on the server should place an abandoned camp within " + SEARCH_RADIUS_CHUNKS + " chunks");

		server.runCommand("gamemode spectator " + player);
		server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
		connection.waitForChunksRender();

		BlockPos campfire = server.computeOnServer(s -> WildfrontClientGameTest.findCampfire(s.overworld(), located));
		check(campfire != null, "the camp located at " + located.toShortString() + " should have generated its campfire");
		check(server.computeOnServer(s -> BlockPos.betweenClosedStream(campfire.offset(-6, -1, -6), campfire.offset(6, 2, 6))
				.anyMatch(pos -> s.overworld().getBlockEntity(pos) instanceof ChestBlockEntity chest
						&& WildfrontLootTables.ABANDONED_CAMP.equals(chest.getLootTable()))),
				"the generated camp should contain its loot chest");
		int offGround = server.computeOnServer(s -> WildfrontClientGameTest.heightAboveGround(s.overworld(), campfire));
		check(Math.abs(offGround) <= 1, "the camp should stand at ground level, but its campfire is " + offGround + " block(s) off");
		int spread = server.computeOnServer(s -> WildfrontClientGameTest.terrainSpread(s.overworld(), located));
		check(spread <= 3, "camps should only generate on roughly level ground, but the terrain around this one varies by " + spread + " blocks");
		System.out.println("[WildfrontDedicatedServerGameTest] natural abandoned camp at " + campfire.toShortString() + ", terrain varies by " + spread + " block(s), campfire " + offGround + " block(s) off ground level");

		server.runCommand("tp " + player + " " + (campfire.getX() - 6.5) + " " + (campfire.getY() + 8) + " " + (campfire.getZ() - 6.5) + " -45 40");
		connection.waitForChunksRender();
		context.waitTicks(20);
		// The camp can be far from spawn; its chunks reach the client over the network a little
		// after the teleport. Fails with a timeout if the client never sees the campfire.
		context.waitFor(client -> client.level != null && client.level.getBlockState(campfire).is(Blocks.CAMPFIRE), CLIENT_SYNC_TIMEOUT_TICKS);
		connection.waitForChunksRender();
		context.waitTicks(40);
		context.takeScreenshot("6_dedicated_server_camp");
	}

	/**
	 * Summons a swordsman and an archer on the server and waits until the client, on the other
	 * side of a real connection, knows both as guards holding what the server gave them.
	 */
	private static void guardsOverTheNetwork(ClientGameTestContext context, TestDedicatedServerContext server,
			TestDedicatedServerConnection connection, String player) {
		server.runCommand("gamemode spectator " + player);
		server.runCommand("difficulty normal");
		BlockPos at = server.computeOnServer(s -> s.getPlayerList().getPlayerByName(player).blockPosition());
		int x = at.getX();
		int y = 200;
		int z = at.getZ();

		server.runCommand("fill " + (x - 3) + " " + y + " " + (z - 5) + " " + (x + 3) + " " + y + " " + (z + 5) + " minecraft:smooth_stone");
		server.runCommand("summon wildfront:guard " + (x - 0.5) + " " + (y + 1) + " " + (z + 0.5)
				+ " {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],equipment:{mainhand:{id:\"minecraft:iron_sword\"}}}");
		server.runCommand("summon wildfront:guard " + (x + 1.5) + " " + (y + 1) + " " + (z + 0.5)
				+ " {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],equipment:{mainhand:{id:\"minecraft:bow\"}}}");

		try {
			check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypeTest.forClass(GuardEntity.class), guard -> true).size()) == 2,
					"the server should have two guards");
			server.runCommand("tp " + player + " " + (x + 0.5) + " " + (y + 1.2) + " " + (z - 3.0) + " 0 5");
			connection.waitForChunksRender();

			context.waitFor(client -> {
				if (client.level == null) {
					return false;
				}

				int swords = 0;
				int bows = 0;

				for (Entity entity : client.level.entitiesForRendering()) {
					if (entity instanceof GuardEntity guard) {
						swords += guard.getMainHandItem().is(Items.IRON_SWORD) ? 1 : 0;
						bows += guard.getMainHandItem().is(Items.BOW) ? 1 : 0;
					}
				}

				return swords == 1 && bows == 1;
			}, CLIENT_SYNC_TIMEOUT_TICKS);
			System.out.println("[WildfrontDedicatedServerGameTest] the client sees a guard with a sword and a guard with a bow");

			context.waitTicks(220);
			context.takeScreenshot("5_server_guards");
		} finally {
			server.runCommand("kill @e[type=wildfront:guard]");
			server.runCommand("kill @e[type=minecraft:item]");
			server.runCommand("fill " + (x - 3) + " " + y + " " + (z - 5) + " " + (x + 3) + " " + y + " " + (z + 5) + " minecraft:air");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
