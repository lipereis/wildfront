package dev.wildfront.config;

import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;

/**
 * Validated settings of one event. The same keys exist for every event.
 *
 * @param enabled              whether the event triggers on its own
 * @param cooldownSeconds      minimum time between two triggers of this event for the same player
 * @param triggerChance        chance per check, once all cooldowns are over, that the event triggers
 * @param minDistanceFromSpawn horizontal distance in blocks from world spawn required; 0 for none
 * @param timeOfDay            part of the day the event is restricted to
 * @param weather              weather the event is restricted to
 */
public record EventConfig(boolean enabled, int cooldownSeconds, double triggerChance, int minDistanceFromSpawn,
		TimeOfDay timeOfDay, Weather weather) {
}
