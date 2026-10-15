package dev.wildfront.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machinery of a Mechanist's Vault: which gauges each lever switches. Contains no Minecraft
 * types so the puzzle can be unit tested.
 *
 * <p>Each lever flips a set of gauges: pulling it lights those that were dark and darkens those
 * that were lit, and pulling it back undoes exactly that. A gauge burns when an odd number of the
 * levers wired to it are on. The vault opens when the gauges show the target marked above them;
 * its secret compartment when the test switch is on and they show the maintenance pattern laid
 * out in the floor. Every lever can be pulled back, so there is no wrong move to recover from.
 *
 * @param leverGauges for each lever, the gauges it switches
 * @param target      the gauges that must burn to open the vault
 * @param maintenance the gauges that must burn, with the test switch on, to open the compartment
 */
public record VaultCircuit(List<Set<Integer>> leverGauges, Set<Integer> target, Set<Integer> maintenance) {
	public static final int LEVERS = 5;
	public static final int GAUGES = 6;

	/**
	 * The circuit of each variant, by the variant's template name.
	 */
	public static final Map<String, VaultCircuit> VARIANTS = Map.of(
			"variant_1", new VaultCircuit(List.of(Set.of(0, 1), Set.of(1, 2, 3), Set.of(3, 4), Set.of(0, 4, 5), Set.of(2, 4)),
					Set.of(1, 3, 5), Set.of(0, 2, 3)),
			"variant_2", new VaultCircuit(List.of(Set.of(2, 3), Set.of(0, 5), Set.of(1, 2, 4), Set.of(3, 4, 5), Set.of(0, 4)),
					Set.of(1, 2, 5), Set.of(1, 3, 4)),
			"variant_3", new VaultCircuit(List.of(Set.of(0, 3, 4), Set.of(1, 5), Set.of(0, 2), Set.of(2, 3, 5), Set.of(1, 3)),
					Set.of(0, 1, 2, 4), Set.of(0, 1, 2, 5)));

	/**
	 * @param levers which levers are on, by lever number
	 * @return which gauges burn
	 */
	public boolean[] gauges(boolean[] levers) {
		boolean[] gauges = new boolean[GAUGES];

		for (int lever = 0; lever < leverGauges.size(); lever++) {
			if (lever < levers.length && levers[lever]) {
				for (int gauge : leverGauges.get(lever)) {
					gauges[gauge] = !gauges[gauge];
				}
			}
		}

		return gauges;
	}

	public static boolean shows(boolean[] gauges, Set<Integer> pattern) {
		for (int gauge = 0; gauge < gauges.length; gauge++) {
			if (gauges[gauge] != pattern.contains(gauge)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * @return every setting of the levers that makes the gauges show the pattern
	 */
	public List<boolean[]> solutions(Set<Integer> pattern) {
		List<boolean[]> solutions = new ArrayList<>();

		for (int combination = 0; combination < 1 << LEVERS; combination++) {
			boolean[] levers = new boolean[LEVERS];

			for (int lever = 0; lever < LEVERS; lever++) {
				levers[lever] = (combination >> lever & 1) == 1;
			}

			if (shows(gauges(levers), pattern)) {
				solutions.add(levers);
			}
		}

		return solutions;
	}
}
