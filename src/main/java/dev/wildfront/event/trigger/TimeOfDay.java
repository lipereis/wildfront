package dev.wildfront.event.trigger;

/**
 * Part of the day an event is restricted to, based on the 24000-tick day cycle.
 */
public enum TimeOfDay {
	ANY,
	DAY,
	NIGHT;

	public static final long DAY_LENGTH = 24_000;
	static final long NIGHT_START = 13_000;
	static final long NIGHT_END = 23_000;

	/**
	 * @param clockTime world clock time in ticks; any value, including totals spanning many days
	 */
	public boolean matches(long clockTime) {
		if (this == ANY) {
			return true;
		}

		long timeOfDay = Math.floorMod(clockTime, DAY_LENGTH);
		boolean night = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
		return (this == NIGHT) == night;
	}
}
