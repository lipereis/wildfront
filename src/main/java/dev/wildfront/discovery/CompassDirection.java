package dev.wildfront.discovery;

/**
 * The eight compass directions, in clockwise order starting at north.
 */
public enum CompassDirection {
	NORTH("north"),
	NORTH_EAST("north-east"),
	EAST("east"),
	SOUTH_EAST("south-east"),
	SOUTH("south"),
	SOUTH_WEST("south-west"),
	WEST("west"),
	NORTH_WEST("north-west");

	private static final CompassDirection[] VALUES = values();

	private final String displayName;

	CompassDirection(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public String translationKey() {
		return "direction.wildfront." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * @param dx offset along the x axis; positive is east
	 * @param dz offset along the z axis; positive is south
	 * @return the direction closest to the offset; north for a zero offset
	 */
	public static CompassDirection fromOffset(double dx, double dz) {
		if (dx == 0 && dz == 0) {
			return NORTH;
		}

		// Angle measured clockwise from north, which is negative z.
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		return VALUES[Math.floorMod(Math.round(degrees / 45.0), VALUES.length)];
	}
}
