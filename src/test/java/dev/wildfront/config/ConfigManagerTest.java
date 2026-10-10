package dev.wildfront.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

class ConfigManagerTest {
	private static final Logger LOGGER = NOPLogger.NOP_LOGGER;

	@TempDir
	Path directory;

	private final List<String> problems = new ArrayList<>();

	@Test
	void missingFileIsCreatedWithDefaultsAndLoadsBackCleanly() throws IOException {
		Path file = directory.resolve("config").resolve("wildfront.json");

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertTrue(Files.exists(file));
		assertTrue(Files.readString(file).contains("\"cooldownSeconds\": 600"));
		assertTrue(Files.readString(file).contains("\"storm\""));

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertTrue(problems.isEmpty());
	}

	@Test
	void editedFileIsLoaded() throws IOException {
		Path file = directory.resolve("wildfront.json");
		Files.writeString(file, "{\"events\": {\"discovery\": {\"cooldownSeconds\": 20}}}");

		assertEquals(20, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems).event("discovery").cooldownSeconds());
		assertTrue(problems.isEmpty());
	}

	@Test
	void malformedJsonUsesDefaultsAndLeavesTheFileUntouched() throws IOException {
		Path file = directory.resolve("wildfront.json");
		String broken = "{ \"checkIntervalSeconds\": 5, ";
		Files.writeString(file, broken);

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertEquals(1, problems.size());
		assertEquals(broken, Files.readString(file));
	}

	@Test
	void nonObjectRootUsesDefaults() throws IOException {
		Path file = directory.resolve("wildfront.json");
		Files.writeString(file, "[1, 2, 3]");

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertEquals(1, problems.size());
	}

	@Test
	void emptyFileUsesDefaults() throws IOException {
		Path file = directory.resolve("wildfront.json");
		Files.writeString(file, "");

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertFalse(problems.isEmpty());
	}

	@Test
	void invalidValuesAreReportedAsProblems() throws IOException {
		Path file = directory.resolve("wildfront.json");
		Files.writeString(file, "{\"checkIntervalSeconds\": -1}");

		assertEquals(ConfigParserTest.DEFAULT, ConfigManager.load(file, ConfigParserTest.EVENT_DEFAULTS, LOGGER, problems));
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("checkIntervalSeconds"));
	}
}
