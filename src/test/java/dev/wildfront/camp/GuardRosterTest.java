package dev.wildfront.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import dev.wildfront.camp.GuardRoster.Role;
import dev.wildfront.camp.GuardRoster.Strength;

import org.junit.jupiter.api.Test;

class GuardRosterTest {
	private static int brutes(List<Role> roster) {
		return Collections.frequency(roster, Role.BRUTE);
	}

	@Test
	void lowStrengthUsesTheMinimumAndHighTheMaximum() {
		assertEquals(2, GuardRoster.pick(2, 5, Strength.LOW, () -> 0.99).size());
		assertEquals(5, GuardRoster.pick(2, 5, Strength.HIGH, () -> 0.0).size());
	}

	@Test
	void mediumStrengthCoversTheWholeRange() {
		assertEquals(2, GuardRoster.pick(2, 5, Strength.MEDIUM, () -> 0.0).size());
		assertEquals(3, GuardRoster.pick(2, 5, Strength.MEDIUM, () -> 0.26).size());
		assertEquals(5, GuardRoster.pick(2, 5, Strength.MEDIUM, () -> 0.76).size());
		assertEquals(5, GuardRoster.pick(2, 5, Strength.MEDIUM, () -> Math.nextDown(1.0)).size());
	}

	@Test
	void aRangeOfOneValueAlwaysGivesThatValue() {
		for (Strength strength : Strength.values()) {
			assertEquals(3, GuardRoster.pick(3, 3, strength, () -> 0.5).size());
		}
	}

	@Test
	void oneBruteForEveryThreeGuards() {
		assertEquals(0, brutes(GuardRoster.pick(1, 1, Strength.LOW, () -> 0)));
		assertEquals(0, brutes(GuardRoster.pick(2, 2, Strength.LOW, () -> 0)));
		assertEquals(1, brutes(GuardRoster.pick(3, 3, Strength.LOW, () -> 0)));
		assertEquals(1, brutes(GuardRoster.pick(5, 5, Strength.LOW, () -> 0)));
		assertEquals(2, brutes(GuardRoster.pick(6, 6, Strength.LOW, () -> 0)));
		assertEquals(2, brutes(GuardRoster.pick(8, 8, Strength.LOW, () -> 0)));
	}

	@Test
	void archersComeFirstAndEveryoneHasARole() {
		List<Role> roster = GuardRoster.pick(4, 4, Strength.LOW, () -> 0);

		assertEquals(List.of(Role.ARCHER, Role.ARCHER, Role.ARCHER, Role.BRUTE), roster);
		assertTrue(roster.stream().allMatch(role -> role == Role.ARCHER || role == Role.BRUTE));
	}
}
