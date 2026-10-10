package dev.wildfront.event;

/**
 * Scheduling parameters of one event, already validated and converted to game ticks.
 *
 * @param enabled       whether the event may trigger on its own
 * @param cooldownTicks minimum game ticks between two triggers for the same player
 * @param triggerChance probability in [0, 1] that an eligible check actually triggers the event
 */
public record EventSettings(boolean enabled, long cooldownTicks, double triggerChance) {
	public static final int TICKS_PER_SECOND = 20;

	public static EventSettings ofSeconds(boolean enabled, int cooldownSeconds, double triggerChance) {
		return new EventSettings(enabled, (long) cooldownSeconds * TICKS_PER_SECOND, triggerChance);
	}
}
