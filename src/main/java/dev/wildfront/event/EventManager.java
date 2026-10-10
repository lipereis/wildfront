package dev.wildfront.event;

import java.util.Random;

import dev.wildfront.Wildfront;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.event.EventScheduler.Decision;
import dev.wildfront.registry.WildfrontEvents;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side driver of the event system. Every check interval it walks the online players and asks
 * the scheduler whether an event should trigger for each of them. All work happens on the server
 * thread; clients only receive the resulting messages.
 */
public final class EventManager {
	private final EventScheduler scheduler = new EventScheduler(new Random()::nextDouble);

	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
	}

	private void onEndTick(MinecraftServer server) {
		WildfrontConfig config = ConfigManager.get();

		if (!config.eventsEnabled()) {
			return;
		}

		int interval = config.checkIntervalSeconds() * EventSettings.TICKS_PER_SECOND;

		if (server.getTickCount() % interval != 0) {
			return;
		}

		long now = server.overworld().getGameTime();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			evaluate(player, now, config);
		}
	}

	private void evaluate(ServerPlayer player, long now, WildfrontConfig config) {
		if (!player.isAlive() || !player.gameMode().isSurvival()) {
			return;
		}

		EventContext context = new EventContext(player, player.level(), now, config);
		PlayerCooldowns cooldowns = new PlayerCooldowns(player);

		for (WorldEvent event : WildfrontEvents.all()) {
			Decision decision = scheduler.attempt(event.id(), event.settings(config), cooldowns, now,
					() -> event.canTrigger(context), () -> run(event, context));

			// At most one event per player per check keeps events from stacking on top of each other.
			if (decision == Decision.TRIGGERED) {
				return;
			}
		}
	}

	/**
	 * Triggers an event for a player regardless of cooldown, chance and conditions, and starts its cooldown.
	 */
	public void forceTrigger(WorldEvent event, ServerPlayer player) {
		long now = player.level().getServer().overworld().getGameTime();
		EventContext context = new EventContext(player, player.level(), now, ConfigManager.get());
		scheduler.force(event.id(), new PlayerCooldowns(player), now, () -> run(event, context));
	}

	private void run(WorldEvent event, EventContext context) {
		String playerName = context.player().getGameProfile().name();

		try {
			event.trigger(context);
			Wildfront.LOGGER.info("[Wildfront] Event '{}' triggered for {} at {} (game time {})",
					event.id(), playerName, context.player().blockPosition().toShortString(), context.gameTime());
		} catch (RuntimeException e) {
			// The cooldown is already recorded, so a broken event cannot fire again every check.
			Wildfront.LOGGER.error("[Wildfront] Event '{}' failed for {}", event.id(), playerName, e);
		}
	}
}
