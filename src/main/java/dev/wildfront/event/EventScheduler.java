package dev.wildfront.event;

import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Decides whether an event may trigger. Contains no Minecraft types so the rules can be unit tested.
 *
 * <p>Checks run cheapest first: enabled flag, cooldown, chance roll and only then the event's own
 * conditions. The cooldown is recorded before the event action runs, so a failing action cannot
 * cause the event to fire again on the next check.
 */
public final class EventScheduler {
	public enum Decision {
		TRIGGERED,
		DISABLED,
		ON_COOLDOWN,
		CHANCE_FAILED,
		CONDITIONS_NOT_MET
	}

	private final DoubleSupplier random;

	/**
	 * @param random source of values in [0, 1) used for the chance roll
	 */
	public EventScheduler(DoubleSupplier random) {
		this.random = random;
	}

	public Decision attempt(String eventId, EventSettings settings, CooldownTracker cooldowns, long now,
			BooleanSupplier conditions, Runnable action) {
		if (!settings.enabled()) {
			return Decision.DISABLED;
		}

		if (isOnCooldown(cooldowns.lastTrigger(eventId), now, settings.cooldownTicks())) {
			return Decision.ON_COOLDOWN;
		}

		if (random.getAsDouble() >= settings.triggerChance()) {
			return Decision.CHANCE_FAILED;
		}

		if (!conditions.getAsBoolean()) {
			return Decision.CONDITIONS_NOT_MET;
		}

		force(eventId, cooldowns, now, action);
		return Decision.TRIGGERED;
	}

	/**
	 * Triggers the event unconditionally and starts its cooldown.
	 */
	public void force(String eventId, CooldownTracker cooldowns, long now, Runnable action) {
		cooldowns.recordTrigger(eventId, now);
		action.run();
	}

	public static boolean isOnCooldown(OptionalLong lastTrigger, long now, long cooldownTicks) {
		return remainingCooldown(lastTrigger, now, cooldownTicks) > 0;
	}

	/**
	 * @return game ticks until the event may trigger again, 0 if it may trigger now
	 */
	public static long remainingCooldown(OptionalLong lastTrigger, long now, long cooldownTicks) {
		if (lastTrigger.isEmpty()) {
			return 0;
		}

		long elapsed = now - lastTrigger.getAsLong();

		// A recorded time in the future means the data came from a world with a different clock.
		// Treat the cooldown as expired instead of blocking the event for an unknown duration.
		if (elapsed < 0) {
			return 0;
		}

		return Math.max(0, cooldownTicks - elapsed);
	}
}
