package dev.wildfront.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;

import org.junit.jupiter.api.Test;

class ConfigParserTest {
	static final EventConfig DISCOVERY = new EventConfig(true, 600, 0.05, 128, TimeOfDay.ANY, Weather.ANY);
	static final EventConfig STORM = new EventConfig(false, 1800, 0.5, 0, TimeOfDay.NIGHT, Weather.THUNDER);
	static final Map<String, EventConfig> EVENT_DEFAULTS = eventDefaults();
	static final WildfrontConfig DEFAULT = WildfrontConfig.defaults(EVENT_DEFAULTS);

	private final List<String> warnings = new ArrayList<>();

	private static Map<String, EventConfig> eventDefaults() {
		Map<String, EventConfig> defaults = new LinkedHashMap<>();
		defaults.put("discovery", DISCOVERY);
		defaults.put("storm", STORM);
		return defaults;
	}

	private WildfrontConfig parse(String json) {
		return ConfigParser.parse(JsonParser.parseString(json).getAsJsonObject(), EVENT_DEFAULTS, warnings);
	}

	@Test
	void emptyObjectGivesDefaultsForEveryEventWithoutWarnings() {
		assertEquals(DEFAULT, parse("{}"));
		assertEquals(DISCOVERY, DEFAULT.event("discovery"));
		assertEquals(STORM, DEFAULT.event("storm"));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void defaultsSurviveARoundTrip() {
		JsonObject json = ConfigParser.toJson(DEFAULT);

		assertEquals(DEFAULT, ConfigParser.parse(json, EVENT_DEFAULTS, warnings));
		assertTrue(warnings.isEmpty());
		assertEquals("night", json.getAsJsonObject("events").getAsJsonObject("storm").get("timeOfDay").getAsString());
	}

	@Test
	void validValuesAreUsed() {
		WildfrontConfig config = parse("""
				{
					"eventsEnabled": false,
					"checkIntervalSeconds": 2,
					"globalCooldownSeconds": 0,
					"shareRadius": 0,
					"discovery": { "minDistance": 8, "maxDistance": 8, "lifetimeSeconds": 30, "maxActive": 1 },
					"hostileCamps": { "enabled": false, "activationRadius": 16, "minGuards": 1, "maxGuards": 8, "maxLivingGuards": 5, "chiefChance": 1 },
					"events": {
						"discovery": {
							"enabled": false,
							"cooldownSeconds": 15,
							"triggerChance": 1,
							"minDistanceFromSpawn": 0,
							"timeOfDay": "night",
							"weather": "RAIN"
						}
					}
				}""");

		assertEquals(new WildfrontConfig(false, 2, 0, 0, Map.of(
				"discovery", new EventConfig(false, 15, 1.0, 0, TimeOfDay.NIGHT, Weather.RAIN),
				"storm", STORM), new DiscoveryConfig(8, 8, 30, 1), new HostileCampConfig(false, 16, 1, 8, 5, 1.0)), config);
		assertTrue(warnings.isEmpty());
	}

	@Test
	void outOfRangeValuesFallBackToDefaultsWithOneWarningEach() {
		WildfrontConfig config = parse("""
				{
					"checkIntervalSeconds": 0,
					"globalCooldownSeconds": -1,
					"shareRadius": 100000,
					"events": {
						"discovery": {
							"cooldownSeconds": -5,
							"triggerChance": 1.5,
							"minDistanceFromSpawn": 99999999999
						}
					}
				}""");

		assertEquals(DEFAULT, config);
		assertEquals(6, warnings.size(), warnings.toString());
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'checkIntervalSeconds'")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'globalCooldownSeconds'")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'shareRadius'")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'events.discovery.cooldownSeconds'")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'events.discovery.triggerChance'")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("'events.discovery.minDistanceFromSpawn'")));
	}

	@Test
	void wrongTypesFallBackToDefaults() {
		WildfrontConfig config = parse("""
				{
					"eventsEnabled": "yes",
					"checkIntervalSeconds": "ten",
					"events": {
						"discovery": {
							"enabled": 1,
							"cooldownSeconds": 12.5,
							"triggerChance": null,
							"minDistanceFromSpawn": [64],
							"timeOfDay": 5,
							"weather": true
						}
					}
				}""");

		assertEquals(DEFAULT, config);
		assertEquals(8, warnings.size(), warnings.toString());
	}

	@Test
	void unknownEnumValueFallsBackAndListsTheAllowedOnes() {
		WildfrontConfig config = parse("{\"events\": {\"storm\": {\"timeOfDay\": \"dusk\", \"weather\": \"snow\"}}}");

		assertEquals(STORM, config.event("storm"));
		assertEquals(2, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("[any, day, night]"), warnings.get(0));
		assertTrue(warnings.get(0).contains("\"night\""), warnings.get(0));
		assertTrue(warnings.get(1).contains("[any, clear, rain, thunder]"), warnings.get(1));
	}

	@Test
	void enumValuesIgnoreCaseAndSurroundingSpaces() {
		WildfrontConfig config = parse("{\"events\": {\"discovery\": {\"timeOfDay\": \" Day \", \"weather\": \"Thunder\"}}}");

		assertEquals(TimeOfDay.DAY, config.event("discovery").timeOfDay());
		assertEquals(Weather.THUNDER, config.event("discovery").weather());
		assertTrue(warnings.isEmpty());
	}

	@Test
	void numbersWrittenAsStringsAreRejected() {
		assertEquals(DEFAULT, parse("{\"checkIntervalSeconds\": \"5\"}"));
		assertEquals(1, warnings.size());
	}

	@Test
	void sectionsOfTheWrongTypeUseDefaultsForTheWholeSection() {
		assertEquals(DEFAULT, parse("{\"events\": 5}"));
		assertEquals(DEFAULT, parse("{\"events\": {\"discovery\": []}}"));
		assertEquals(2, warnings.size(), warnings.toString());
		assertTrue(warnings.get(1).contains("'events.discovery'"));
	}

	@Test
	void oneBadValueDoesNotDiscardTheOthers() {
		WildfrontConfig config = parse("""
				{ "checkIntervalSeconds": 3, "events": { "discovery": { "cooldownSeconds": 30, "triggerChance": -1 } } }""");

		assertEquals(3, config.checkIntervalSeconds());
		assertEquals(30, config.event("discovery").cooldownSeconds());
		assertEquals(DISCOVERY.triggerChance(), config.event("discovery").triggerChance());
		assertEquals(1, warnings.size());
	}

	@Test
	void eachEventKeepsItsOwnDefaults() {
		WildfrontConfig config = parse("{\"events\": {\"storm\": {\"enabled\": true}}}");

		assertEquals(new EventConfig(true, 1800, 0.5, 0, TimeOfDay.NIGHT, Weather.THUNDER), config.event("storm"));
		assertEquals(DISCOVERY, config.event("discovery"));
	}

	@Test
	void unknownEventIsReportedAndIgnored() {
		WildfrontConfig config = parse("{\"events\": {\"meteor\": {\"enabled\": true}}}");

		assertEquals(DEFAULT, config);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("'events.meteor'"));
		assertThrows(IllegalArgumentException.class, () -> config.event("meteor"));
	}

	@Test
	void unknownKeysAreIgnored() {
		assertEquals(DEFAULT, parse("{\"futureOption\": 1, \"events\": {\"discovery\": {\"other\": true}}}"));
		assertTrue(warnings.isEmpty());
	}

	@Test
	void sectionFromThePreviousFormatIsReported() {
		assertEquals(DEFAULT, parse("{\"mysteriousDiscovery\": {\"cooldownSeconds\": 15}}"));
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("events.mysterious_discovery"));
	}

	@Test
	void discoverySectionFallsBackValueByValue() {
		WildfrontConfig config = parse("""
				{ "discovery": { "minDistance": 2, "maxDistance": 64, "lifetimeSeconds": "long", "maxActive": 0 } }""");

		assertEquals(new DiscoveryConfig(DiscoveryConfig.DEFAULT.minDistance(), 64, DiscoveryConfig.DEFAULT.lifetimeSeconds(),
				DiscoveryConfig.DEFAULT.maxActive()), config.discovery());
		assertEquals(3, warnings.size(), warnings.toString());
	}

	@Test
	void maxDistanceBelowMinDistanceResetsBoth() {
		WildfrontConfig config = parse("{\"discovery\": {\"minDistance\": 60, \"maxDistance\": 20}}");

		assertEquals(DiscoveryConfig.DEFAULT, config.discovery());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("'discovery.maxDistance'"));
	}

	@Test
	void defaultCacheDistancesAreOrdered() {
		assertTrue(DiscoveryConfig.DEFAULT.minDistance() <= DiscoveryConfig.DEFAULT.maxDistance());
	}

	@Test
	void hostileCampSectionFallsBackValueByValue() {
		WildfrontConfig config = parse("""
				{ "hostileCamps": { "enabled": "no", "activationRadius": 500, "minGuards": 2, "maxGuards": 6, "maxLivingGuards": 0, "chiefChance": 1.5 } }""");

		assertEquals(new HostileCampConfig(true, HostileCampConfig.DEFAULT.activationRadius(), 2, 6, HostileCampConfig.DEFAULT.maxLivingGuards(), HostileCampConfig.DEFAULT.chiefChance()), config.hostileCamps());
		assertEquals(4, warnings.size(), warnings.toString());
	}

	@Test
	void maxGuardsBelowMinGuardsResetsBoth() {
		WildfrontConfig config = parse("{\"hostileCamps\": {\"minGuards\": 6, \"maxGuards\": 2}}");

		assertEquals(HostileCampConfig.DEFAULT, config.hostileCamps());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("'hostileCamps.maxGuards'"));
	}

	@Test
	void hostileCampDefaultsAreWrittenAndReadBack() {
		assertEquals(HostileCampConfig.DEFAULT, DEFAULT.hostileCamps());
		assertTrue(ConfigParser.toJson(DEFAULT).has("hostileCamps"));
		assertTrue(HostileCampConfig.DEFAULT.minGuards() <= HostileCampConfig.DEFAULT.maxGuards());
	}

	@Test
	void boundaryValuesAreAccepted() {
		WildfrontConfig config = parse("""
				{ "checkIntervalSeconds": 3600, "globalCooldownSeconds": 604800, "shareRadius": 512,
				  "events": { "discovery": { "cooldownSeconds": 604800, "triggerChance": 0, "minDistanceFromSpawn": 1000000 } } }""");

		assertEquals(3600, config.checkIntervalSeconds());
		assertEquals(604800, config.globalCooldownSeconds());
		assertEquals(512, config.shareRadius());
		assertEquals(new EventConfig(true, 604800, 0.0, 1_000_000, TimeOfDay.ANY, Weather.ANY), config.event("discovery"));
		assertTrue(warnings.isEmpty());
	}
}
