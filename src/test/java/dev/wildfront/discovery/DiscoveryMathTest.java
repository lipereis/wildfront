package dev.wildfront.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class DiscoveryMathTest {
	@Test
	void cardinalDirectionsFollowMinecraftAxes() {
		assertEquals(CompassDirection.NORTH, CompassDirection.fromOffset(0, -10));
		assertEquals(CompassDirection.SOUTH, CompassDirection.fromOffset(0, 10));
		assertEquals(CompassDirection.EAST, CompassDirection.fromOffset(10, 0));
		assertEquals(CompassDirection.WEST, CompassDirection.fromOffset(-10, 0));
	}

	@Test
	void diagonalDirections() {
		assertEquals(CompassDirection.NORTH_EAST, CompassDirection.fromOffset(10, -10));
		assertEquals(CompassDirection.SOUTH_EAST, CompassDirection.fromOffset(10, 10));
		assertEquals(CompassDirection.SOUTH_WEST, CompassDirection.fromOffset(-10, 10));
		assertEquals(CompassDirection.NORTH_WEST, CompassDirection.fromOffset(-10, -10));
	}

	@Test
	void offsetsSnapToTheNearestDirection() {
		assertEquals(CompassDirection.NORTH, CompassDirection.fromOffset(3, -40));
		assertEquals(CompassDirection.NORTH, CompassDirection.fromOffset(-3, -40));
		assertEquals(CompassDirection.NORTH_EAST, CompassDirection.fromOffset(30, -40));
		assertEquals(CompassDirection.EAST, CompassDirection.fromOffset(40, 3));
		assertEquals(CompassDirection.WEST, CompassDirection.fromOffset(-40, -3));
	}

	@Test
	void zeroOffsetHasADirection() {
		assertEquals(CompassDirection.NORTH, CompassDirection.fromOffset(0, 0));
	}

	@Test
	void translationKeysAndNames() {
		assertEquals("direction.wildfront.north_east", CompassDirection.NORTH_EAST.translationKey());
		assertEquals("north-east", CompassDirection.NORTH_EAST.displayName());
	}

	@Test
	void ringOffsetsStayBetweenMinAndMaxDistance() {
		Random random = new Random(42);

		for (int i = 0; i < 2_000; i++) {
			int[] offset = SiteOffsets.ring(random::nextDouble, 24, 48);
			double distance = Math.hypot(offset[0], offset[1]);

			// Rounding each axis to whole blocks can move the point by at most sqrt(0.5).
			assertTrue(distance >= 24 - 0.71 && distance <= 48 + 0.71, "distance " + distance);
		}
	}

	@Test
	void ringOffsetsCoverAllDirections() {
		Random random = new Random(7);
		boolean[] seen = new boolean[CompassDirection.values().length];

		for (int i = 0; i < 500; i++) {
			int[] offset = SiteOffsets.ring(random::nextDouble, 24, 48);
			seen[CompassDirection.fromOffset(offset[0], offset[1]).ordinal()] = true;
		}

		for (CompassDirection direction : CompassDirection.values()) {
			assertTrue(seen[direction.ordinal()], "never picked " + direction);
		}
	}

	@Test
	void equalMinAndMaxGiveAFixedDistance() {
		int[] offset = SiteOffsets.ring(() -> 0.0, 16, 16);

		assertEquals(16, offset[0]);
		assertEquals(0, offset[1]);
	}
}
