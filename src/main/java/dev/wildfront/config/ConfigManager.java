package dev.wildfront.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.slf4j.Logger;

import dev.wildfront.Wildfront;
import dev.wildfront.registry.WildfrontEvents;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads {@code config/wildfront.json} and holds the active configuration.
 */
public final class ConfigManager {
	public static final String FILE_NAME = "wildfront.json";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static volatile WildfrontConfig current;

	private ConfigManager() {
	}

	public static WildfrontConfig get() {
		WildfrontConfig config = current;

		if (config == null) {
			throw new IllegalStateException("Wildfront config was requested before it was loaded");
		}

		return config;
	}

	/**
	 * Reads the config file again and makes the result the active configuration.
	 *
	 * @return the problems found in the file; empty if it was loaded as written
	 */
	public static List<String> reload() {
		List<String> problems = new ArrayList<>();
		current = load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME), WildfrontEvents.defaultConfigs(), Wildfront.LOGGER, problems);
		return problems;
	}

	/**
	 * Loads the file at {@code path}. A missing file is created with defaults. A file that cannot be
	 * read or parsed is left untouched and defaults are used, so a typo never destroys user settings.
	 *
	 * @param eventDefaults default settings of every registered event, keyed by event id
	 */
	public static WildfrontConfig load(Path path, Map<String, EventConfig> eventDefaults, Logger logger, List<String> problems) {
		WildfrontConfig defaults = WildfrontConfig.defaults(eventDefaults);

		if (Files.notExists(path)) {
			writeDefaults(path, defaults, logger);
			return defaults;
		}

		JsonElement root;

		try {
			root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException e) {
			problems.add("could not read the file (" + e.getMessage() + "); using defaults for everything");
			logger.error("[Wildfront] Could not read config {}; using defaults. Fix or delete the file. Cause: {}", path, e.getMessage());
			return defaults;
		}

		if (!root.isJsonObject()) {
			problems.add("the file must contain a JSON object; using defaults for everything");
			logger.error("[Wildfront] Config {} must contain a JSON object; using defaults. Fix or delete the file.", path);
			return defaults;
		}

		WildfrontConfig config = ConfigParser.parse(root.getAsJsonObject(), eventDefaults, problems);

		for (String problem : problems) {
			logger.warn("[Wildfront] Config {}: {}", path.getFileName(), problem);
		}

		logger.info("[Wildfront] Loaded config: {}", config);
		return config;
	}

	private static void writeDefaults(Path path, WildfrontConfig defaults, Logger logger) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}

			Files.writeString(path, GSON.toJson(ConfigParser.toJson(defaults)) + System.lineSeparator(), StandardCharsets.UTF_8);
			logger.info("[Wildfront] Created default config at {}", path);
		} catch (IOException e) {
			logger.error("[Wildfront] Could not create default config at {}; using defaults in memory. Cause: {}", path, e.getMessage());
		}
	}
}
