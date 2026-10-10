package dev.wildfront.config;

/**
 * Validated configuration. Instances only ever hold values inside the documented ranges;
 * see {@link ConfigParser} for how raw file content is turned into one.
 *
 * @param eventsEnabled        master switch for automatic events
 * @param checkIntervalSeconds how often the event manager looks at the online players
 */
public record WildfrontConfig(boolean eventsEnabled, int checkIntervalSeconds, MysteriousDiscovery mysteriousDiscovery) {
	public static final WildfrontConfig DEFAULT = new WildfrontConfig(true, 10, MysteriousDiscovery.DEFAULT);

	/**
	 * @param cooldownSeconds      minimum time between two triggers for the same player
	 * @param triggerChance        chance per check, once the cooldown is over, that the event triggers
	 * @param minDistanceFromSpawn horizontal distance in blocks from world spawn required to trigger
	 */
	public record MysteriousDiscovery(boolean enabled, int cooldownSeconds, double triggerChance, int minDistanceFromSpawn) {
		public static final MysteriousDiscovery DEFAULT = new MysteriousDiscovery(true, 600, 0.05, 128);
	}
}
