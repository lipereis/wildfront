package dev.wildfront.config;

/**
 * Validated settings of the forgotten cache placed by the Mysterious Discovery event.
 *
 * @param minDistance     closest the cache appears to the triggering player, in blocks
 * @param maxDistance     furthest the cache appears from the triggering player; never below {@code minDistance}
 * @param lifetimeSeconds how long an unopened cache stays before it is removed
 * @param maxActive       how many caches may exist at once on the server
 */
public record DiscoveryConfig(int minDistance, int maxDistance, int lifetimeSeconds, int maxActive) {
	public static final DiscoveryConfig DEFAULT = new DiscoveryConfig(24, 48, 600, 8);
}
