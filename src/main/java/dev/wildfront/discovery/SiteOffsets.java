package dev.wildfront.discovery;

import java.util.function.DoubleSupplier;

/**
 * Picks horizontal offsets for places where a discovery may appear.
 */
public final class SiteOffsets {
	private SiteOffsets() {
	}

	/**
	 * @param random source of values in [0, 1)
	 * @return {dx, dz} in a random direction, between {@code minDistance} and {@code maxDistance}
	 *         blocks away before rounding to whole blocks
	 */
	public static int[] ring(DoubleSupplier random, int minDistance, int maxDistance) {
		double angle = random.getAsDouble() * 2 * Math.PI;
		double distance = minDistance + random.getAsDouble() * (maxDistance - minDistance);
		return new int[] {(int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance)};
	}
}
