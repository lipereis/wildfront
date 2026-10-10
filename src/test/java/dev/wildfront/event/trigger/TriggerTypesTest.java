package dev.wildfront.event.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.wildfront.config.EventConfig;

import org.junit.jupiter.api.Test;

class TriggerTypesTest {
	@Test
	void anyTimeAlwaysMatches() {
		for (long time : new long[] {0, 6_000, 13_000, 18_000, 23_999, -1}) {
			assertTrue(TimeOfDay.ANY.matches(time));
		}
	}

	@Test
	void nightIsFrom13000To23000() {
		assertFalse(TimeOfDay.NIGHT.matches(12_999));
		assertTrue(TimeOfDay.NIGHT.matches(13_000));
		assertTrue(TimeOfDay.NIGHT.matches(18_000));
		assertTrue(TimeOfDay.NIGHT.matches(22_999));
		assertFalse(TimeOfDay.NIGHT.matches(23_000));
	}

	@Test
	void dayIsTheOppositeOfNight() {
		for (long time = 0; time < TimeOfDay.DAY_LENGTH; time += 250) {
			assertEquals(!TimeOfDay.NIGHT.matches(time), TimeOfDay.DAY.matches(time), "time " + time);
		}
	}

	@Test
	void timeWrapsAcrossDaysAndHandlesNegativeValues() {
		assertTrue(TimeOfDay.NIGHT.matches(24_000L * 500 + 18_000));
		assertTrue(TimeOfDay.DAY.matches(24_000L * 500 + 6_000));
		assertTrue(TimeOfDay.NIGHT.matches(-6_000));
	}

	@Test
	void weatherMatching() {
		assertTrue(Weather.ANY.matches(false, false));
		assertTrue(Weather.ANY.matches(true, true));

		assertTrue(Weather.CLEAR.matches(false, false));
		assertFalse(Weather.CLEAR.matches(true, false));

		assertTrue(Weather.RAIN.matches(true, false));
		assertTrue(Weather.RAIN.matches(true, true));
		assertFalse(Weather.RAIN.matches(false, false));

		assertTrue(Weather.THUNDER.matches(true, true));
		assertFalse(Weather.THUNDER.matches(true, false));
		assertFalse(Weather.THUNDER.matches(false, true));
	}

	@Test
	void allMetStopsAtTheFirstFailure() {
		AtomicInteger laterChecks = new AtomicInteger();
		List<TriggerCondition> conditions = List.of(
				TriggerCondition.of("first", context -> true),
				TriggerCondition.of("second", context -> false),
				TriggerCondition.of("third", context -> laterChecks.incrementAndGet() > 0));

		assertFalse(TriggerCondition.allMet(conditions, null));
		assertEquals(0, laterChecks.get());
		assertTrue(TriggerCondition.allMet(List.of(), null));
		assertTrue(TriggerCondition.allMet(conditions.subList(0, 1), null));
	}

	@Test
	void unmetListsEveryFailingConditionByName() {
		List<TriggerCondition> conditions = List.of(
				TriggerCondition.of("first", context -> false),
				TriggerCondition.of("second", context -> true),
				TriggerCondition.of("third", context -> false));

		assertEquals(List.of("first", "third"), TriggerCondition.unmet(conditions, null));
	}

	@Test
	void unrestrictedConfigProducesNoConditions() {
		assertTrue(TriggerConditions.fromConfig(new EventConfig(true, 600, 0.05, 0, TimeOfDay.ANY, Weather.ANY)).isEmpty());
	}

	@Test
	void eachRestrictionProducesOneNamedCondition() {
		List<String> names = TriggerConditions.fromConfig(new EventConfig(true, 600, 0.05, 128, TimeOfDay.NIGHT, Weather.THUNDER))
				.stream().map(TriggerCondition::name).toList();

		assertEquals(List.of("at least 128 blocks from world spawn", "time of day NIGHT", "weather THUNDER"), names);
	}
}
