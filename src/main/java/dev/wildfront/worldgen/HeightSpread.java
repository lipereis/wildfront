package dev.wildfront.worldgen;

/**
 * Judges whether a patch of terrain is level enough to build on, from surface heights sampled
 * across it. Contains no Minecraft types so the rule can be unit tested.
 */
public final class HeightSpread {
	private HeightSpread() {
	}

	/**
	 * Offsets of a 3x3 grid of sample points covering a square of the given radius around a centre:
	 * {@code {dx, dz}} pairs, centre included.
	 */
	public static int[][] sampleOffsets(int radius) {
		int[][] offsets = new int[9][];
		int index = 0;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				offsets[index++] = new int[] {dx * radius, dz * radius};
			}
		}

		return offsets;
	}

	/**
	 * @return the difference between the highest and the lowest of the given heights; 0 for none
	 */
	public static int spread(int[] heights) {
		if (heights.length == 0) {
			return 0;
		}

		int min = heights[0];
		int max = heights[0];

		for (int height : heights) {
			min = Math.min(min, height);
			max = Math.max(max, height);
		}

		return max - min;
	}

	public static boolean isLevel(int[] heights, int maxDifference) {
		return spread(heights) <= maxDifference;
	}

	/**
	 * @return whether the point is at least {@code minDistance} blocks from the origin, horizontally
	 */
	public static boolean isFarEnough(int x, int z, int minDistance) {
		return (long) x * x + (long) z * z >= (long) minDistance * minDistance;
	}

	/**
	 * @param surface heights of the topmost block of any kind, water included
	 * @param floor   heights of the topmost solid block at the same points
	 * @return whether any point has water (or another fluid) standing on its ground
	 */
	public static boolean hasWater(int[] surface, int[] floor) {
		for (int i = 0; i < surface.length; i++) {
			if (surface[i] > floor[i]) {
				return true;
			}
		}

		return false;
	}
}
