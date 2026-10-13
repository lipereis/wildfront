package dev.wildfront.camp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Decides who guards a camp. Contains no Minecraft types so the rules can be unit tested.
 */
public final class GuardRoster {
	public enum Role {
		/** Ranged: a guard with a bow. */
		ARCHER,
		/** Melee: a guard with a sword. */
		BRUTE
	}

	/** How much of the configured range a world's difficulty uses. */
	public enum Strength {
		LOW,
		MEDIUM,
		HIGH
	}

	private GuardRoster() {
	}

	/**
	 * @param min    smallest number of guards, used at {@link Strength#LOW}
	 * @param max    largest number of guards, used at {@link Strength#HIGH}; never below {@code min}
	 * @param random source of values in [0, 1), used at {@link Strength#MEDIUM} to pick within the range
	 * @return the guards to spawn: one brute for every three guards, the rest archers
	 */
	public static List<Role> pick(int min, int max, Strength strength, DoubleSupplier random) {
		int count = switch (strength) {
			case LOW -> min;
			case HIGH -> max;
			case MEDIUM -> min + (int) Math.floor(random.getAsDouble() * (max - min + 1));
		};
		count = Math.max(min, Math.min(max, count));

		int brutes = count / 3;
		List<Role> roster = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			roster.add(i < count - brutes ? Role.ARCHER : Role.BRUTE);
		}

		return roster;
	}
}
