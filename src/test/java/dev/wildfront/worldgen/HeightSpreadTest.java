package dev.wildfront.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class HeightSpreadTest {
	@Test
	void sampleOffsetsFormAThreeByThreeGridIncludingTheCentre() {
		int[][] offsets = HeightSpread.sampleOffsets(7);

		assertEquals(9, offsets.length);
		assertTrue(Arrays.stream(offsets).anyMatch(offset -> offset[0] == 0 && offset[1] == 0));
		assertTrue(Arrays.stream(offsets).anyMatch(offset -> offset[0] == -7 && offset[1] == -7));
		assertTrue(Arrays.stream(offsets).anyMatch(offset -> offset[0] == 7 && offset[1] == 7));
		assertTrue(Arrays.stream(offsets).allMatch(offset -> Math.abs(offset[0]) % 7 == 0 && Math.abs(offset[1]) % 7 == 0));
		assertEquals(9, Arrays.stream(offsets).map(offset -> offset[0] + "," + offset[1]).distinct().count());
		assertArrayEquals(new int[] {0, 0}, HeightSpread.sampleOffsets(1)[4]);
	}

	@Test
	void spreadIsHighestMinusLowest() {
		assertEquals(0, HeightSpread.spread(new int[] {64, 64, 64}));
		assertEquals(5, HeightSpread.spread(new int[] {64, 69, 66}));
		assertEquals(10, HeightSpread.spread(new int[] {-4, 6}));
		assertEquals(0, HeightSpread.spread(new int[] {70}));
		assertEquals(0, HeightSpread.spread(new int[0]));
	}

	@Test
	void levelMeansSpreadWithinTheLimit() {
		int[] gentle = {64, 65, 66, 67, 65, 64, 66, 67, 65};
		int[] hillside = {64, 66, 69, 65, 67, 70, 66, 68, 72};

		assertTrue(HeightSpread.isLevel(gentle, 3));
		assertFalse(HeightSpread.isLevel(gentle, 2));
		assertFalse(HeightSpread.isLevel(hillside, 3));
		assertTrue(HeightSpread.isLevel(hillside, 8));
		assertTrue(HeightSpread.isLevel(new int[] {64, 64}, 0));
	}

	@Test
	void waterIsDetectedWhereTheSurfaceIsAboveTheGround() {
		assertFalse(HeightSpread.hasWater(new int[] {64, 65, 66}, new int[] {64, 65, 66}));
		assertTrue(HeightSpread.hasWater(new int[] {62, 62, 62}, new int[] {62, 60, 62}));
		assertFalse(HeightSpread.hasWater(new int[0], new int[0]));
	}
}
