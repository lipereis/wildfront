package dev.wildfront.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ConfigParserTest {
	private final List<String> warnings = new ArrayList<>();

	private WildfrontConfig parse(String json) {
		return ConfigParser.parse(JsonParser.parseString(json).getAsJsonObject(), warnings);
	}

	@Test
	void emptyObjectGivesDefaultsWithoutWarnings() {
		assertEquals(WildfrontConfig.DEFAULT, parse("{}"));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void defaultsSurviveARoundTrip() {
		JsonObject json = ConfigParser.toJson(WildfrontConfig.DEFAULT);

		assertEquals(WildfrontConfig.DEFAULT, ConfigParser.parse(json, warnings));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void validValuesAreUsed() {
		WildfrontConfig config = parse("""
				{
					"eventsEnabled": false,
					"checkIntervalSeconds": 2,
					"mysteriousDiscovery": {
						"enabled": false,
						"cooldownSeconds": 15,
						"triggerChance": 1,
						"minDistanceFromSpawn": 0
					}
				}""");

		assertEquals(new WildfrontConfig(false, 2, new WildfrontConfig.MysteriousDiscovery(false, 15, 1.0, 0)), config);
		assertTrue(warnings.isEmpty());
	}

	@Test
	void outOfRangeValuesFallBackToDefaultsWithOneWarningEach() {
		WildfrontConfig config = parse("""
				{
					"checkIntervalSeconds": 0,
					"mysteriousDiscovery": {
						"cooldownSeconds": -5,
						"triggerChance": 1.5,
						"minDistanceFromSpawn": 99999999999
					}
				}""");

		assertEquals(WildfrontConfig.DEFAULT, config);
		assertEquals(4, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("checkIntervalSeconds"));
		assertTrue(warnings.get(1).contains("mysteriousDiscovery.cooldownSeconds"));
		assertTrue(warnings.get(2).contains("mysteriousDiscovery.triggerChance"));
		assertTrue(warnings.get(3).contains("mysteriousDiscovery.minDistanceFromSpawn"));
	}

	@Test
	void wrongTypesFallBackToDefaults() {
		WildfrontConfig config = parse("""
				{
					"eventsEnabled": "yes",
					"checkIntervalSeconds": "ten",
					"mysteriousDiscovery": {
						"enabled": 1,
						"cooldownSeconds": 12.5,
						"triggerChance": null,
						"minDistanceFromSpawn": [64]
					}
				}""");

		assertEquals(WildfrontConfig.DEFAULT, config);
		assertEquals(6, warnings.size(), warnings.toString());
	}

	@Test
	void numbersWrittenAsStringsAreRejected() {
		assertEquals(WildfrontConfig.DEFAULT, parse("{\"checkIntervalSeconds\": \"5\"}"));
		assertEquals(1, warnings.size());
	}

	@Test
	void sectionOfTheWrongTypeUsesDefaultsForTheWholeSection() {
		assertEquals(WildfrontConfig.DEFAULT, parse("{\"mysteriousDiscovery\": 5}"));
		assertEquals(1, warnings.size());
	}

	@Test
	void oneBadValueDoesNotDiscardTheOthers() {
		WildfrontConfig config = parse("""
				{ "checkIntervalSeconds": 3, "mysteriousDiscovery": { "cooldownSeconds": 30, "triggerChance": -1 } }""");

		assertEquals(3, config.checkIntervalSeconds());
		assertEquals(30, config.mysteriousDiscovery().cooldownSeconds());
		assertEquals(WildfrontConfig.MysteriousDiscovery.DEFAULT.triggerChance(), config.mysteriousDiscovery().triggerChance());
		assertEquals(1, warnings.size());
	}

	@Test
	void unknownKeysAreIgnored() {
		assertEquals(WildfrontConfig.DEFAULT, parse("{\"futureOption\": 1, \"mysteriousDiscovery\": {\"other\": true}}"));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void boundaryValuesAreAccepted() {
		WildfrontConfig config = parse("""
				{ "checkIntervalSeconds": 3600, "mysteriousDiscovery": { "cooldownSeconds": 604800, "triggerChance": 0, "minDistanceFromSpawn": 1000000 } }""");

		assertEquals(3600, config.checkIntervalSeconds());
		assertEquals(604800, config.mysteriousDiscovery().cooldownSeconds());
		assertEquals(0.0, config.mysteriousDiscovery().triggerChance());
		assertEquals(1_000_000, config.mysteriousDiscovery().minDistanceFromSpawn());
		assertTrue(warnings.isEmpty());
	}
}
