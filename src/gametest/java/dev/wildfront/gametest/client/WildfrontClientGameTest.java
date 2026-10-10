package dev.wildfront.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mojang.datafixers.util.Pair;

import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

		System.out.println("[WildfrontClientGameTest] natural abandoned camp at " + campfire.toShortString());
		lookAt(context, world, player, campfire);
		takeWorldScreenshot(context, "1_abandoned_camp");
		server.runCommand("tp " + player + " " + (campfire.getX() + 7.5) + " " + (campfire.getY() + 4) + " " + (campfire.getZ() + 7.5) + " 135 25");
		context.waitTicks(20);
		takeWorldScreenshot(context, "1_abandoned_camp_from_south_east");
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

	private static BlockPos findCampfire(ServerLevel level, BlockPos near) {
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
