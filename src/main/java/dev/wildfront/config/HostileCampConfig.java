package dev.wildfront.config;

/**
 * Validated settings of bandit camps.
 *
 * @param enabled          whether guards are spawned at all; camps still generate when false
 * @param activationRadius how close, in blocks, a player must come for a camp's guards to appear
 * @param minGuards        number of guards on easy difficulty
 * @param maxGuards        number of guards on hard difficulty; never below {@code minGuards}
 * @param maxLivingGuards  no camp is activated while this many guards are alive on the server
 */
public record HostileCampConfig(boolean enabled, int activationRadius, int minGuards, int maxGuards, int maxLivingGuards) {
	public static final HostileCampConfig DEFAULT = new HostileCampConfig(true, 48, 2, 3, 40);
}
