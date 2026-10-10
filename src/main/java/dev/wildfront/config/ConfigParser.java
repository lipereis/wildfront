package dev.wildfront.config;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Converts between JSON and {@link WildfrontConfig}. Every missing, mistyped or out-of-range value
 * falls back to its default and adds a human-readable warning; parsing never throws.
 */
public final class ConfigParser {
	static final int MAX_CHECK_INTERVAL_SECONDS = 3600;
	static final int MAX_COOLDOWN_SECONDS = 7 * 24 * 3600;
	static final int MAX_DISTANCE = 1_000_000;

	private ConfigParser() {
	}

	public static WildfrontConfig parse(JsonObject root, List<String> warnings) {
		WildfrontConfig defaults = WildfrontConfig.DEFAULT;
		WildfrontConfig.MysteriousDiscovery discoveryDefaults = defaults.mysteriousDiscovery();
		JsonObject discovery = readObject(root, "mysteriousDiscovery", warnings);

		return new WildfrontConfig(
				readBoolean(root, "", "eventsEnabled", defaults.eventsEnabled(), warnings),
				readInt(root, "", "checkIntervalSeconds", defaults.checkIntervalSeconds(), 1, MAX_CHECK_INTERVAL_SECONDS, warnings),
				new WildfrontConfig.MysteriousDiscovery(
						readBoolean(discovery, "mysteriousDiscovery.", "enabled", discoveryDefaults.enabled(), warnings),
						readInt(discovery, "mysteriousDiscovery.", "cooldownSeconds", discoveryDefaults.cooldownSeconds(), 0, MAX_COOLDOWN_SECONDS, warnings),
						readDouble(discovery, "mysteriousDiscovery.", "triggerChance", discoveryDefaults.triggerChance(), 0.0, 1.0, warnings),
						readInt(discovery, "mysteriousDiscovery.", "minDistanceFromSpawn", discoveryDefaults.minDistanceFromSpawn(), 0, MAX_DISTANCE, warnings)));
	}

	public static JsonObject toJson(WildfrontConfig config) {
		JsonObject discovery = new JsonObject();
		discovery.addProperty("enabled", config.mysteriousDiscovery().enabled());
		discovery.addProperty("cooldownSeconds", config.mysteriousDiscovery().cooldownSeconds());
		discovery.addProperty("triggerChance", config.mysteriousDiscovery().triggerChance());
		discovery.addProperty("minDistanceFromSpawn", config.mysteriousDiscovery().minDistanceFromSpawn());

		JsonObject root = new JsonObject();
		root.addProperty("eventsEnabled", config.eventsEnabled());
		root.addProperty("checkIntervalSeconds", config.checkIntervalSeconds());
		root.add("mysteriousDiscovery", discovery);
		return root;
	}

	private static JsonObject readObject(JsonObject parent, String key, List<String> warnings) {
		JsonElement element = parent.get(key);

		if (element == null) {
			return new JsonObject();
		}

		if (!element.isJsonObject()) {
			warnings.add("'" + key + "' must be an object; using defaults for all of its values");
			return new JsonObject();
		}

		return element.getAsJsonObject();
	}

	private static boolean readBoolean(JsonObject object, String path, String key, boolean fallback, List<String> warnings) {
		JsonElement element = object.get(key);

		if (element == null) {
			return fallback;
		}

		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
			return element.getAsBoolean();
		}

		warnings.add("'" + path + key + "' must be true or false but was " + element + "; using default " + fallback);
		return fallback;
	}

	private static int readInt(JsonObject object, String path, String key, int fallback, int min, int max, List<String> warnings) {
		Double value = readNumber(object, path, key, fallback, min, max, warnings);

		if (value == null) {
			return fallback;
		}

		if (value != Math.rint(value)) {
			warnings.add("'" + path + key + "' must be a whole number but was " + value + "; using default " + fallback);
			return fallback;
		}

		return value.intValue();
	}

	private static double readDouble(JsonObject object, String path, String key, double fallback, double min, double max, List<String> warnings) {
		Double value = readNumber(object, path, key, fallback, min, max, warnings);
		return value == null ? fallback : value;
	}

	/**
	 * @return the value if present, numeric and within [min, max]; otherwise null
	 */
	private static Double readNumber(JsonObject object, String path, String key, Object fallback, double min, double max, List<String> warnings) {
		JsonElement element = object.get(key);

		if (element == null) {
			return null;
		}

		if (!element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber()) {
			warnings.add("'" + path + key + "' must be a number but was " + element + "; using default " + fallback);
			return null;
		}

		double value;

		try {
			value = element.getAsDouble();
		} catch (NumberFormatException e) {
			warnings.add("'" + path + key + "' is not a valid number: " + element + "; using default " + fallback);
			return null;
		}

		// The comparison is written so that NaN also fails it.
		if (!(value >= min && value <= max)) {
			warnings.add("'" + path + key + "' must be between " + format(min) + " and " + format(max)
					+ " but was " + element + "; using default " + fallback);
			return null;
		}

		return value;
	}

	private static String format(double bound) {
		return bound == Math.rint(bound) ? Long.toString((long) bound) : Double.toString(bound);
	}
}
