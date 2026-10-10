package dev.wildfront.config;

import java.util.Map;

/**
 * Validated configuration. Instances only ever hold values inside the documented ranges;
 * see {@link ConfigParser} for how raw file content is turned into one.
 *
 * @param eventsEnabled         master switch for automatic events
 * @param checkIntervalSeconds  how often the event manager looks at the online players
 * @param globalCooldownSeconds minimum time between any two events for the same player
 * @param shareRadius           players within this many blocks of a triggering player witness the
 *                              same event instead of getting their own; 0 to disable
 * @param events                settings per event id; contains every registered event
 * @param discovery             settings of the forgotten cache
 */
public record WildfrontConfig(boolean eventsEnabled, int checkIntervalSeconds, int globalCooldownSeconds, int shareRadius,
		Map<String, EventConfig> events, DiscoveryConfig discovery) {
	public static final boolean DEFAULT_EVENTS_ENABLED = true;
	public static final int DEFAULT_CHECK_INTERVAL_SECONDS = 10;
	public static final int DEFAULT_GLOBAL_COOLDOWN_SECONDS = 120;
	public static final int DEFAULT_SHARE_RADIUS = 48;

	public WildfrontConfig {
		events = Map.copyOf(events);
	}

	public static WildfrontConfig defaults(Map<String, EventConfig> eventDefaults) {
		return new WildfrontConfig(DEFAULT_EVENTS_ENABLED, DEFAULT_CHECK_INTERVAL_SECONDS, DEFAULT_GLOBAL_COOLDOWN_SECONDS,
				DEFAULT_SHARE_RADIUS, eventDefaults, DiscoveryConfig.DEFAULT);
	}

	/**
	 * @throws IllegalArgumentException if no event with that id was registered when the config was loaded
	 */
	public EventConfig event(String id) {
		EventConfig config = events.get(id);

		if (config == null) {
			throw new IllegalArgumentException("No configuration for event '" + id + "'");
		}

		return config;
	}
}
