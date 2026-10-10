package dev.wildfront.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;

/**
 * Converts between JSON and {@link WildfrontConfig}. Every missing, mistyped or out-of-range value
 * falls back to its default and adds a human-readable warning; parsing never throws.
 */
public final class ConfigParser {
	static final int MAX_CHECK_INTERVAL_SECONDS = 3600;
	static final int MAX_COOLDOWN_SECONDS = 7 * 24 * 3600;
	static final int MAX_DISTANCE = 1_000_000;
	static final int MAX_SHARE_RADIUS = 512;

	private static final String EVENTS = "events";
	private static final String LEGACY_MYSTERIOUS_DISCOVERY = "mysteriousDiscovery";

	private ConfigParser() {
	}

	/**
	 * @param eventDefaults default settings of every registered event, keyed by event id
	 */
	public static WildfrontConfig parse(JsonObject root, Map<String, EventConfig> eventDefaults, List<String> warnings) {
		if (root.has(LEGACY_MYSTERIOUS_DISCOVERY)) {
			warnings.add("'" + LEGACY_MYSTERIOUS_DISCOVERY + "' is no longer read; move its values to 'events.mysterious_discovery'");
		}

		JsonObject eventsJson = readObject(root, "", EVENTS, warnings);
		Map<String, EventConfig> events = new LinkedHashMap<>();

		for (Map.Entry<String, EventConfig> entry : eventDefaults.entrySet()) {
			String path = EVENTS + "." + entry.getKey() + ".";
			events.put(entry.getKey(), parseEvent(readObject(eventsJson, EVENTS + ".", entry.getKey(), warnings), path, entry.getValue(), warnings));
		}

		for (String id : eventsJson.keySet()) {
			if (!eventDefaults.containsKey(id)) {
				warnings.add("'" + EVENTS + "." + id + "' is not a known event and is ignored; known events: " + eventDefaults.keySet());
			}
		}

		return new WildfrontConfig(
				readBoolean(root, "", "eventsEnabled", WildfrontConfig.DEFAULT_EVENTS_ENABLED, warnings),
				readInt(root, "", "checkIntervalSeconds", WildfrontConfig.DEFAULT_CHECK_INTERVAL_SECONDS, 1, MAX_CHECK_INTERVAL_SECONDS, warnings),
				readInt(root, "", "globalCooldownSeconds", WildfrontConfig.DEFAULT_GLOBAL_COOLDOWN_SECONDS, 0, MAX_COOLDOWN_SECONDS, warnings),
				readInt(root, "", "shareRadius", WildfrontConfig.DEFAULT_SHARE_RADIUS, 0, MAX_SHARE_RADIUS, warnings),
				events);
	}

	private static EventConfig parseEvent(JsonObject json, String path, EventConfig defaults, List<String> warnings) {
		return new EventConfig(
				readBoolean(json, path, "enabled", defaults.enabled(), warnings),
				readInt(json, path, "cooldownSeconds", defaults.cooldownSeconds(), 0, MAX_COOLDOWN_SECONDS, warnings),
				readDouble(json, path, "triggerChance", defaults.triggerChance(), 0.0, 1.0, warnings),
				readInt(json, path, "minDistanceFromSpawn", defaults.minDistanceFromSpawn(), 0, MAX_DISTANCE, warnings),
				readEnum(json, path, "timeOfDay", defaults.timeOfDay(), TimeOfDay.class, warnings),
				readEnum(json, path, "weather", defaults.weather(), Weather.class, warnings));
	}

	public static JsonObject toJson(WildfrontConfig config) {
		JsonObject events = new JsonObject();

		for (Map.Entry<String, EventConfig> entry : new java.util.TreeMap<>(config.events()).entrySet()) {
			EventConfig event = entry.getValue();
			JsonObject json = new JsonObject();
			json.addProperty("enabled", event.enabled());
			json.addProperty("cooldownSeconds", event.cooldownSeconds());
			json.addProperty("triggerChance", event.triggerChance());
			json.addProperty("minDistanceFromSpawn", event.minDistanceFromSpawn());
			json.addProperty("timeOfDay", name(event.timeOfDay()));
			json.addProperty("weather", name(event.weather()));
			events.add(entry.getKey(), json);
		}

		JsonObject root = new JsonObject();
		root.addProperty("eventsEnabled", config.eventsEnabled());
		root.addProperty("checkIntervalSeconds", config.checkIntervalSeconds());
		root.addProperty("globalCooldownSeconds", config.globalCooldownSeconds());
		root.addProperty("shareRadius", config.shareRadius());
		root.add(EVENTS, events);
		return root;
	}

	private static String name(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject parent, String path, String key, List<String> warnings) {
		JsonElement element = parent.get(key);

		if (element == null) {
			return new JsonObject();
		}

		if (!element.isJsonObject()) {
			warnings.add("'" + path + key + "' must be an object; using defaults for all of its values");
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

	private static <E extends Enum<E>> E readEnum(JsonObject object, String path, String key, E fallback, Class<E> type, List<String> warnings) {
		JsonElement element = object.get(key);

		if (element == null) {
			return fallback;
		}

		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equalsIgnoreCase(element.getAsString().trim())) {
					return constant;
				}
			}
		}

		List<String> allowed = Arrays.stream(type.getEnumConstants()).map(ConfigParser::name).toList();
		warnings.add("'" + path + key + "' must be one of " + allowed + " but was " + element + "; using default \"" + name(fallback) + "\"");
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
