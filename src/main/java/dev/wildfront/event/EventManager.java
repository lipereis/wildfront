package dev.wildfront.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.wildfront.Wildfront;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.config.EventConfig;
import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.event.EventScheduler.Decision;
import dev.wildfront.event.trigger.TriggerCondition;
import dev.wildfront.event.trigger.TriggerConditions;
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
	private static final String NOT_ELIGIBLE = "alive and in survival or adventure mode";
	private static final String EVENT_SPECIFIC = "event-specific requirements";

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
		if (!isEligible(player)) {
			return;
		}

		PlayerCooldowns cooldowns = new PlayerCooldowns(player);
		long globalCooldown = EventSettings.ticks(config.globalCooldownSeconds());

		// Most checks end here: a player who recently had or witnessed an event needs no further work.
		if (EventScheduler.isOnCooldown(cooldowns.lastTrigger(EventScheduler.ANY_EVENT), now, globalCooldown)) {
			return;
		}

		for (WorldEvent event : WildfrontEvents.all()) {
			EventConfig eventConfig = config.event(event.id());
			EventContext context = new EventContext(player, player.level(), now, eventConfig, List.of(player));

			Decision decision = scheduler.attempt(event.id(), EventSettings.of(eventConfig), globalCooldown, cooldowns, now,
					() -> TriggerCondition.allMet(TriggerConditions.fromConfig(eventConfig), context) && event.canTrigger(context),
					() -> fire(event, player, now, config));

			// At most one event per player per check keeps events from stacking on top of each other.
			if (decision == Decision.TRIGGERED) {
				return;
			}
		}
	}

	/**
	 * Triggers an event for a player regardless of cooldowns, chance and conditions, and starts the cooldowns.
	 *
	 * @return everyone who experienced the event: the player plus nearby players who shared it
	 */
	public List<ServerPlayer> forceTrigger(WorldEvent event, ServerPlayer player) {
		long now = player.level().getServer().overworld().getGameTime();
		List<ServerPlayer> participants = new ArrayList<>();
		scheduler.force(event.id(), new PlayerCooldowns(player), now,
				() -> participants.addAll(fire(event, player, now, ConfigManager.get())));
		return participants;
	}

	/**
	 * @return what currently keeps the event from triggering for the player, apart from cooldowns
	 *         and chance; empty if nothing does
	 */
	public List<String> unmetConditions(WorldEvent event, ServerPlayer player) {
		if (!isEligible(player)) {
			return List.of(NOT_ELIGIBLE);
		}

		EventConfig eventConfig = ConfigManager.get().event(event.id());
		long now = player.level().getServer().overworld().getGameTime();
		EventContext context = new EventContext(player, player.level(), now, eventConfig, List.of(player));
		List<String> unmet = new ArrayList<>(TriggerCondition.unmet(TriggerConditions.fromConfig(eventConfig), context));

		if (!event.canTrigger(context)) {
			unmet.add(EVENT_SPECIFIC);
		}

		return unmet;
	}

	/**
	 * Runs the event once for the triggering player and everyone sharing it. The triggering
	 * player's cooldowns are already recorded by the scheduler; witnesses get theirs here, before
	 * the event runs, so one occurrence can never lead to a second one for somebody standing next to it.
	 */
	private List<ServerPlayer> fire(WorldEvent event, ServerPlayer player, long now, WildfrontConfig config) {
		List<ServerPlayer> participants = participants(player, config.shareRadius());

		for (ServerPlayer participant : participants) {
			if (participant != player) {
				EventScheduler.record(event.id(), new PlayerCooldowns(participant), now);
			}
		}

		EventContext context = new EventContext(player, player.level(), now, config.event(event.id()), participants);
		String names = participants.stream().map(participant -> participant.getGameProfile().name()).toList().toString();

		try {
			event.trigger(context);
			Wildfront.LOGGER.info("[Wildfront] Event '{}' triggered for {} at {} (game time {})",
					event.id(), names, player.blockPosition().toShortString(), now);
		} catch (RuntimeException e) {
			// The cooldowns are already recorded, so a broken event cannot fire again every check.
			Wildfront.LOGGER.error("[Wildfront] Event '{}' failed for {}", event.id(), names, e);
		}

		return participants;
	}

	private static List<ServerPlayer> participants(ServerPlayer player, int shareRadius) {
		List<ServerPlayer> participants = new ArrayList<>();
		participants.add(player);

		if (shareRadius > 0) {
			for (ServerPlayer other : player.level().players()) {
				if (other != player && isEligible(other) && other.closerThan(player, shareRadius)) {
					participants.add(other);
				}
			}
		}

		return participants;
	}

	private static boolean isEligible(ServerPlayer player) {
		return player.isAlive() && player.gameMode().isSurvival();
	}
}
