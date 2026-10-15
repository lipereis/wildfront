package dev.wildfront.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VaultCircuitTest {
	private static final VaultCircuit SIMPLE = new VaultCircuit(
			List.of(Set.of(0, 1), Set.of(1, 2), Set.of(2), Set.of(3), Set.of(4, 5)), Set.of(0, 2), Set.of(5));

	@Test
	void aLeverFlipsItsGaugesAndPullingItBackUndoesThat() {
		assertArrayEquals(new boolean[] {true, true, false, false, false, false}, SIMPLE.gauges(new boolean[] {true, false, false, false, false}));
		// Gauge 1 is wired to two levers: both on cancel out.
		assertArrayEquals(new boolean[] {true, false, true, false, false, false}, SIMPLE.gauges(new boolean[] {true, true, false, false, false}));
		assertArrayEquals(new boolean[6], SIMPLE.gauges(new boolean[5]));
	}

	@Test
	void showsComparesEveryGauge() {
		assertTrue(VaultCircuit.shows(new boolean[] {true, false, true, false, false, false}, Set.of(0, 2)));
		assertFalse(VaultCircuit.shows(new boolean[] {true, false, true, true, false, false}, Set.of(0, 2)));
		assertFalse(VaultCircuit.shows(new boolean[6], Set.of(0)));
	}

	@Test
	void everyVariantHasThreeTemplates() {
		assertEquals(Set.of("variant_1", "variant_2", "variant_3"), VaultCircuit.VARIANTS.keySet());
	}

	/**
	 * The solvability check of each variant: the vault's target and the compartment's
	 * maintenance pattern can each be reached by exactly one setting of the levers, they differ,
	 * and neither is what the vault shows with every lever off, as it generates.
	 */
	@Test
	void variant1IsSolvable() {
		checkSolvable("variant_1");
	}

	@Test
	void variant2IsSolvable() {
		checkSolvable("variant_2");
	}

	@Test
	void variant3IsSolvable() {
		checkSolvable("variant_3");
	}

	private static void checkSolvable(String variant) {
		VaultCircuit circuit = VaultCircuit.VARIANTS.get(variant);
		assertEquals(VaultCircuit.LEVERS, circuit.leverGauges().size(), variant + " should have one wiring per lever");

		for (Set<Integer> gauges : circuit.leverGauges()) {
			assertFalse(gauges.isEmpty(), variant + ": every lever should switch something");
			assertTrue(gauges.stream().allMatch(gauge -> gauge >= 0 && gauge < VaultCircuit.GAUGES), variant + ": wiring to a gauge that does not exist");
		}

		assertEquals(1, circuit.solutions(circuit.target()).size(), variant + ": the vault's target should have exactly one solution");
		assertEquals(1, circuit.solutions(circuit.maintenance()).size(), variant + ": the maintenance pattern should have exactly one solution");
		assertNotEquals(circuit.target(), circuit.maintenance(), variant + ": the two patterns must differ");
		assertFalse(VaultCircuit.shows(circuit.gauges(new boolean[VaultCircuit.LEVERS]), circuit.target()), variant + ": a fresh vault must not already be open");
		assertFalse(VaultCircuit.shows(circuit.gauges(new boolean[VaultCircuit.LEVERS]), circuit.maintenance()), variant + ": a fresh vault must not already show the maintenance pattern");

		// No two settings look alike, so what a player sees always tells them where they are.
		Set<String> seen = new HashSet<>();

		for (int combination = 0; combination < 1 << VaultCircuit.LEVERS; combination++) {
			boolean[] levers = new boolean[VaultCircuit.LEVERS];

			for (int lever = 0; lever < VaultCircuit.LEVERS; lever++) {
				levers[lever] = (combination >> lever & 1) == 1;
			}

			assertTrue(seen.add(java.util.Arrays.toString(circuit.gauges(levers))), variant + ": two lever settings show the same gauges");
		}
	}

	@Test
	void variantsAreNotTheSamePuzzle() {
		Map<String, VaultCircuit> variants = VaultCircuit.VARIANTS;
		assertNotEquals(variants.get("variant_1").leverGauges(), variants.get("variant_2").leverGauges());
		assertNotEquals(variants.get("variant_2").leverGauges(), variants.get("variant_3").leverGauges());
		assertNotEquals(variants.get("variant_1").leverGauges(), variants.get("variant_3").leverGauges());
	}
}
