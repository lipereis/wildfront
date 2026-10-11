package dev.wildfront.gametest;

import java.util.Map;
import java.util.OptionalLong;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wildfront.Wildfront;
import dev.wildfront.config.DiscoveryConfig;
import dev.wildfront.config.EventConfig;
import dev.wildfront.config.HostileCampConfig;
import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.event.EventScheduler;
import dev.wildfront.event.PlayerCooldowns;
import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;
import dev.wildfront.registry.WildfrontEvents;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Runs the event system end to end on a real server: the automatic check, sharing between players,
 * the commands, and the cost of the code that runs on a timer.
 */
public class EventSystemGameTest {
	private static final String EVENT = WildfrontEvents.MYSTERIOUS_DISCOVERY.id();
	private static final int COOLDOWN_SECONDS = 60;
	private static final long COOLDOWN_TICKS = COOLDOWN_SECONDS * 20L;

	/**
	 * Events always trigger when allowed and place their cache within the test area.
	 */
	private static WildfrontConfig config(boolean eventsEnabled, double chance, int globalCooldownSeconds, TimeOfDay timeOfDay) {
		return new WildfrontConfig(eventsEnabled, 1, globalCooldownSeconds, 16,
				Map.of(EVENT, new EventConfig(true, COOLDOWN_SECONDS, chance, 0, timeOfDay, Weather.ANY)),
				new DiscoveryConfig(1, 3, 60, 64), HostileCampConfig.DEFAULT);
	}

	private static long cachesNear(ServerLevel level, BlockPos origin) {
		return DiscoveryManager.activeCaches(level).stream().filter(cache -> cache.pos().closerThan(origin, 6)).count();
	}

	@GameTest(skyAccess = true)
	public void automaticCheckTriggersOncePerCooldownAndIsSharedByNearbyPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameTestSupport.buildFloor(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
		long now = level.getGameTime();
		ServerPlayer first = null;
		ServerPlayer second = null;
		ServerPlayer creative = null;

		try {
			first = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);
			second = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 4, 1, 3);
			creative = GameTestSupport.addPlayer(helper, GameType.CREATIVE, 3, 1, 4);
			WildfrontConfig config = config(true, 1.0, 0, TimeOfDay.ANY);

			Wildfront.EVENTS.checkPlayers(server, now, config(false, 1.0, 0, TimeOfDay.ANY));
			helper.assertTrue(cachesNear(level, origin) == 0, "nothing should trigger while events are disabled");

			Wildfront.EVENTS.checkPlayers(server, now, config(true, 0.0, 0, TimeOfDay.ANY));
			helper.assertTrue(cachesNear(level, origin) == 0, "nothing should trigger with a chance of zero");

			// Whatever time it is, one of DAY and NIGHT does not match.
			TimeOfDay wrongTime = TimeOfDay.DAY.matches(level.getOverworldClockTime()) ? TimeOfDay.NIGHT : TimeOfDay.DAY;
			Wildfront.EVENTS.checkPlayers(server, now, config(true, 1.0, 0, wrongTime));
			helper.assertTrue(cachesNear(level, origin) == 0, "nothing should trigger at the wrong time of day");
			helper.assertTrue(new PlayerCooldowns(first).lastTrigger(EVENT).isEmpty(), "failed checks should not start a cooldown");

			Wildfront.EVENTS.checkPlayers(server, now, config);
			helper.assertTrue(cachesNear(level, origin) == 1, "two players standing together should get one event, not " + cachesNear(level, origin));
			helper.assertTrue(new PlayerCooldowns(first).lastTrigger(EVENT).equals(OptionalLong.of(now)), "the first player should be on cooldown");
			helper.assertTrue(new PlayerCooldowns(second).lastTrigger(EVENT).equals(OptionalLong.of(now)), "the second player should share the cooldown");
			helper.assertTrue(new PlayerCooldowns(second).lastTrigger(EventScheduler.ANY_EVENT).equals(OptionalLong.of(now)), "the witness should also get the global cooldown");
			helper.assertTrue(new PlayerCooldowns(creative).lastTrigger(EVENT).isEmpty(), "a creative player should not take part");

			Wildfront.EVENTS.checkPlayers(server, now, config);
			Wildfront.EVENTS.checkPlayers(server, now + COOLDOWN_TICKS - 1, config);
			helper.assertTrue(cachesNear(level, origin) == 1, "nothing should trigger again during the cooldown");

			Wildfront.EVENTS.checkPlayers(server, now + COOLDOWN_TICKS, config);
			helper.assertTrue(cachesNear(level, origin) == 2, "the event should trigger again once the cooldown is over");
		} finally {
			GameTestSupport.remove(helper, first, second, creative);
			GameTestSupport.removeCaches(helper);
		}

		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void globalCooldownDelaysTheNextEvent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameTestSupport.buildFloor(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
		long now = level.getGameTime();
		ServerPlayer player = null;

		try {
			player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);
			int globalSeconds = COOLDOWN_SECONDS * 2;
			WildfrontConfig config = config(true, 1.0, globalSeconds, TimeOfDay.ANY);

			Wildfront.EVENTS.checkPlayers(server, now, config);
			Wildfront.EVENTS.checkPlayers(server, now + COOLDOWN_TICKS, config);
			helper.assertTrue(cachesNear(level, origin) == 1, "the global cooldown should outlast the event cooldown");

			Wildfront.EVENTS.checkPlayers(server, now + globalSeconds * 20L, config);
			helper.assertTrue(cachesNear(level, origin) == 2, "the event should trigger once the global cooldown is over");
		} finally {
			GameTestSupport.remove(helper, player);
			GameTestSupport.removeCaches(helper);
		}

		helper.succeed();
	}

	@GameTest
	public void commandsWorkAndRequirePermission(GameTestHelper helper) throws CommandSyntaxException {
		MinecraftServer server = helper.getLevel().getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
		ServerPlayer player = null;

		try {
			player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);
			String name = player.getGameProfile().name();

			helper.assertTrue(dispatcher.execute("wildfront event list", console) == WildfrontEvents.all().size(), "list should report every event");
			helper.assertTrue(dispatcher.execute("wildfront event list", player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput())
					== WildfrontEvents.all().size(), "list should also work for a player, including their cooldowns and conditions");
			helper.assertTrue(dispatcher.execute("wildfront reload", console) == 1, "reload should succeed with the default config");

			new PlayerCooldowns(player).recordTrigger(EVENT, 5);
			helper.assertTrue(dispatcher.execute("wildfront event reset " + name, console) == 1, "reset should succeed");
			helper.assertTrue(new PlayerCooldowns(player).lastTrigger(EVENT).isEmpty(), "reset should clear the player's cooldowns");

			boolean rejected = false;

			try {
				dispatcher.execute("wildfront event trigger no_such_event " + name, console);
			} catch (CommandSyntaxException e) {
				rejected = e.getMessage().contains("no_such_event");
			}

			helper.assertTrue(rejected, "an unknown event id should be rejected with a message naming it");
			helper.assertFalse(dispatcher.getRoot().getChild("wildfront").canUse(console.withPermission(PermissionSet.NO_PERMISSIONS)),
					"players without permission should not be able to use /wildfront");
		} finally {
			GameTestSupport.remove(helper, player);
		}

		helper.succeed();
	}

	/**
	 * Measures the code that runs on a timer. The limits are far above the expected cost; they exist
	 * to catch a change that makes these paths scan blocks or load chunks, not to benchmark precisely.
	 */
	@GameTest(skyAccess = true)
	public void timerDrivenCodeIsCheap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameTestSupport.buildFloor(helper);
		long now = level.getGameTime();
		ServerPlayer player = null;

		try {
			player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 3, 1, 3);
			WildfrontConfig onCooldown = config(true, 1.0, 120, TimeOfDay.ANY);
			WildfrontConfig neverTriggers = config(true, 0.0, 0, TimeOfDay.ANY);
			EventScheduler.record(EVENT, new PlayerCooldowns(player), now);

			long cooldownPath = nanosPerRun(20_000, () -> Wildfront.EVENTS.checkPlayers(server, now + 1, onCooldown));

			new PlayerCooldowns(player).clear();
			long fullPath = nanosPerRun(20_000, () -> Wildfront.EVENTS.checkPlayers(server, now + 1, neverTriggers));

			for (int x = 0; x < 8; x++) {
				DiscoveryManager.place(level, helper.absolutePos(new BlockPos(x, 1, 6)), Long.MAX_VALUE / 2);
			}

			int caches = DiscoveryManager.activeCaches(level).size();
			long cacheLook = nanosPerRun(2_000, () -> DiscoveryManager.tick(level, now + 1));

			for (int x = 0; x < 8; x++) {
				helper.setBlock(x, 1, 6, Blocks.AIR);
			}

			DiscoveryManager.tick(level, now + 1);
			helper.killAllEntities();

			Wildfront.LOGGER.info("[Wildfront] PERF player check on cooldown: {} ns; player check, all conditions evaluated: {} ns; "
					+ "cache look with {} active caches: {} ns", cooldownPath, fullPath, caches, cacheLook);
			helper.assertTrue(cooldownPath < 100_000, "a player check on cooldown took " + cooldownPath + " ns");
			helper.assertTrue(fullPath < 100_000, "a full player check took " + fullPath + " ns");
			helper.assertTrue(cacheLook < 1_000_000, "a look at " + caches + " caches took " + cacheLook + " ns");
		} finally {
			GameTestSupport.remove(helper, player);
			GameTestSupport.removeCaches(helper);
		}

		helper.succeed();
	}

	private static long nanosPerRun(int runs, Runnable action) {
		// Warm up so the measurement is of compiled code.
		for (int i = 0; i < runs / 4; i++) {
			action.run();
		}

		long start = System.nanoTime();

		for (int i = 0; i < runs; i++) {
			action.run();
		}

		return (System.nanoTime() - start) / runs;
	}
}
