package dev.wildfront.event.trigger;

/**
 * Weather an event is restricted to. {@code RAIN} also matches thunderstorms.
 */
public enum Weather {
	ANY,
	CLEAR,
	RAIN,
	THUNDER;

	public boolean matches(boolean raining, boolean thundering) {
		return switch (this) {
			case ANY -> true;
			case CLEAR -> !raining;
			case RAIN -> raining;
			case THUNDER -> raining && thundering;
		};
	}
}
