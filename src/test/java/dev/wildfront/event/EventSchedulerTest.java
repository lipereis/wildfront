package dev.wildfront.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import dev.wildfront.event.EventScheduler.Decision;

import org.junit.jupiter.api.Test;

class EventSchedulerTest {
	private static final String EVENT = "test_event";
	private static final long COOLDOWN = 1200;
	private static final long GLOBAL_COOLDOWN = 0;
	private static final EventSettings ALWAYS = new EventSettings(true, COOLDOWN, 1.0);

	private final MapCooldownTracker cooldowns = new MapCooldownTracker();
	private final AtomicInteger triggers = new AtomicInteger();
	private final AtomicInteger conditionChecks = new AtomicInteger();

	private Decision attempt(EventScheduler scheduler, EventSettings settings, long now, boolean conditionsMet) {
		return scheduler.attempt(EVENT, settings, GLOBAL_COOLDOWN, cooldowns, now, () -> {
			conditionChecks.incrementAndGet();
			return conditionsMet;
		}, triggers::incrementAndGet);
	}

	private static EventScheduler rolling(double roll) {
		return new EventScheduler(() -> roll);
	}

	@Test
	void triggersWhenNeverTriggeredBefore() {
		assertEquals(Decision.TRIGGERED, attempt(rolling(0.0), ALWAYS, 100, true));
		assertEquals(1, triggers.get());
		assertEquals(OptionalLong.of(100), cooldowns.lastTrigger(EVENT));
	}

	@Test
	void disabledEventNeverTriggers() {
		EventSettings disabled = new EventSettings(false, 0, 1.0);

		assertEquals(Decision.DISABLED, attempt(rolling(0.0), disabled, 100, true));
		assertEquals(0, triggers.get());
		assertEquals(0, conditionChecks.get());
	}

	@Test
	void repeatedChecksInTheSameTickTriggerOnlyOnce() {
		EventScheduler scheduler = rolling(0.0);

		assertEquals(Decision.TRIGGERED, attempt(scheduler, ALWAYS, 100, true));
		assertEquals(Decision.ON_COOLDOWN, attempt(scheduler, ALWAYS, 100, true));
		assertEquals(Decision.ON_COOLDOWN, attempt(scheduler, ALWAYS, 100, true));
		assertEquals(1, triggers.get());
	}

	@Test
	void cooldownBlocksUntilExactlyElapsed() {
		EventScheduler scheduler = rolling(0.0);
		attempt(scheduler, ALWAYS, 100, true);

		assertEquals(Decision.ON_COOLDOWN, attempt(scheduler, ALWAYS, 100 + COOLDOWN - 1, true));
		assertEquals(1, triggers.get());

		assertEquals(Decision.TRIGGERED, attempt(scheduler, ALWAYS, 100 + COOLDOWN, true));
		assertEquals(2, triggers.get());
		assertEquals(OptionalLong.of(100 + COOLDOWN), cooldowns.lastTrigger(EVENT));
	}

	@Test
	void cooldownChecksDoNotEvaluateConditionsOrRollChance() {
		AtomicInteger rolls = new AtomicInteger();
		EventScheduler scheduler = new EventScheduler(() -> {
			rolls.incrementAndGet();
			return 0.0;
		});
		attempt(scheduler, ALWAYS, 100, true);
		int rollsAfterTrigger = rolls.get();
		int checksAfterTrigger = conditionChecks.get();

		attempt(scheduler, ALWAYS, 101, true);

		assertEquals(rollsAfterTrigger, rolls.get());
		assertEquals(checksAfterTrigger, conditionChecks.get());
	}

	@Test
	void failedChanceDoesNotStartCooldownOrEvaluateConditions() {
		EventSettings halfChance = new EventSettings(true, COOLDOWN, 0.5);

		assertEquals(Decision.CHANCE_FAILED, attempt(rolling(0.5), halfChance, 100, true));
		assertEquals(0, triggers.get());
		assertEquals(0, conditionChecks.get());
		assertTrue(cooldowns.lastTrigger(EVENT).isEmpty());

		assertEquals(Decision.TRIGGERED, attempt(rolling(0.49), halfChance, 101, true));
	}

	@Test
	void zeroChanceNeverTriggersAndFullChanceAlwaysDoes() {
		assertEquals(Decision.CHANCE_FAILED, attempt(rolling(0.0), new EventSettings(true, COOLDOWN, 0.0), 100, true));
		assertEquals(Decision.TRIGGERED, attempt(rolling(Math.nextDown(1.0)), ALWAYS, 100, true));
	}

	@Test
	void unmetConditionsDoNotStartCooldown() {
		assertEquals(Decision.CONDITIONS_NOT_MET, attempt(rolling(0.0), ALWAYS, 100, false));
		assertEquals(0, triggers.get());
		assertTrue(cooldowns.lastTrigger(EVENT).isEmpty());

		assertEquals(Decision.TRIGGERED, attempt(rolling(0.0), ALWAYS, 101, true));
	}

	@Test
	void cooldownIsRecordedEvenWhenTheActionFails() {
		EventScheduler scheduler = rolling(0.0);

		assertThrows(IllegalStateException.class, () -> scheduler.attempt(EVENT, ALWAYS, GLOBAL_COOLDOWN, cooldowns, 100, () -> true, () -> {
			throw new IllegalStateException("broken event");
		}));

		assertEquals(Decision.ON_COOLDOWN, attempt(scheduler, ALWAYS, 101, true));
		assertEquals(0, triggers.get());
	}

	@Test
	void cooldownsAreIndependentPerEventAndPerTracker() {
		EventScheduler scheduler = rolling(0.0);
		attempt(scheduler, ALWAYS, 100, true);

		MapCooldownTracker otherPlayer = new MapCooldownTracker();
		assertEquals(Decision.TRIGGERED, scheduler.attempt(EVENT, ALWAYS, GLOBAL_COOLDOWN, otherPlayer, 100, () -> true, triggers::incrementAndGet));
		assertEquals(Decision.TRIGGERED, scheduler.attempt("other_event", ALWAYS, GLOBAL_COOLDOWN, cooldowns, 100, () -> true, triggers::incrementAndGet));
		assertEquals(3, triggers.get());
	}

	@Test
	void forceIgnoresCooldownAndRestartsIt() {
		EventScheduler scheduler = rolling(0.0);
		attempt(scheduler, ALWAYS, 100, true);

		scheduler.force(EVENT, cooldowns, 500, triggers::incrementAndGet);

		assertEquals(2, triggers.get());
		assertEquals(Decision.ON_COOLDOWN, attempt(scheduler, ALWAYS, 100 + COOLDOWN, true));
		assertEquals(Decision.TRIGGERED, attempt(scheduler, ALWAYS, 500 + COOLDOWN, true));
	}

	@Test
	void zeroCooldownAllowsTriggerOnEveryCheck() {
		EventSettings noCooldown = new EventSettings(true, 0, 1.0);
		EventScheduler scheduler = rolling(0.0);

		assertEquals(Decision.TRIGGERED, attempt(scheduler, noCooldown, 100, true));
		assertEquals(Decision.TRIGGERED, attempt(scheduler, noCooldown, 100, true));
	}

	@Test
	void lastTriggerInTheFutureIsTreatedAsExpired() {
		cooldowns.recordTrigger(EVENT, 1_000_000);

		assertFalse(EventScheduler.isOnCooldown(cooldowns.lastTrigger(EVENT), 50, COOLDOWN));
		assertEquals(Decision.TRIGGERED, attempt(rolling(0.0), ALWAYS, 50, true));
	}

	@Test
	void remainingCooldownCountsDown() {
		assertEquals(0, EventScheduler.remainingCooldown(OptionalLong.empty(), 100, COOLDOWN));
		assertEquals(COOLDOWN, EventScheduler.remainingCooldown(OptionalLong.of(100), 100, COOLDOWN));
		assertEquals(1, EventScheduler.remainingCooldown(OptionalLong.of(100), 100 + COOLDOWN - 1, COOLDOWN));
		assertEquals(0, EventScheduler.remainingCooldown(OptionalLong.of(100), 100 + COOLDOWN, COOLDOWN));
		assertEquals(0, EventScheduler.remainingCooldown(OptionalLong.of(100), Long.MAX_VALUE, COOLDOWN));
	}

	@Test
	void anHourOfChecksNeverTriggersFasterThanTheCooldown() {
		EventScheduler scheduler = rolling(0.0);
		int checkInterval = 200;
		long hour = 72_000;

		for (long now = 0; now < hour; now += checkInterval) {
			attempt(scheduler, ALWAYS, now, true);
		}

		assertEquals(hour / COOLDOWN, triggers.get());
	}

	@Test
	void globalCooldownBlocksOtherEventsButNotForever() {
		EventScheduler scheduler = rolling(0.0);
		long global = 400;
		scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100, () -> true, triggers::incrementAndGet);

		assertEquals(Decision.ON_GLOBAL_COOLDOWN, scheduler.attempt("other_event", ALWAYS, global, cooldowns, 100 + global - 1, () -> true, triggers::incrementAndGet));
		assertEquals(1, triggers.get());

		assertEquals(Decision.TRIGGERED, scheduler.attempt("other_event", ALWAYS, global, cooldowns, 100 + global, () -> true, triggers::incrementAndGet));
		assertEquals(2, triggers.get());
	}

	@Test
	void eventCooldownStillAppliesAfterGlobalCooldownEnds() {
		EventScheduler scheduler = rolling(0.0);
		long global = 400;
		scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100, () -> true, triggers::incrementAndGet);

		assertEquals(Decision.ON_COOLDOWN, scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100 + global, () -> true, triggers::incrementAndGet));
		assertEquals(Decision.TRIGGERED, scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100 + COOLDOWN, () -> true, triggers::incrementAndGet));
	}

	@Test
	void globalCooldownLongerThanEventCooldownWins() {
		EventScheduler scheduler = rolling(0.0);
		long global = COOLDOWN * 2;
		scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100, () -> true, triggers::incrementAndGet);

		assertEquals(Decision.ON_GLOBAL_COOLDOWN, scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100 + COOLDOWN, () -> true, triggers::incrementAndGet));
		assertEquals(Decision.TRIGGERED, scheduler.attempt(EVENT, ALWAYS, global, cooldowns, 100 + global, () -> true, triggers::incrementAndGet));
	}

	@Test
	void failedAttemptsDoNotStartGlobalCooldown() {
		assertEquals(Decision.CONDITIONS_NOT_MET, rolling(0.0).attempt(EVENT, ALWAYS, 400, cooldowns, 100, () -> false, triggers::incrementAndGet));
		assertEquals(Decision.CHANCE_FAILED, rolling(0.9).attempt(EVENT, new EventSettings(true, COOLDOWN, 0.5), 400, cooldowns, 100, () -> true, triggers::incrementAndGet));

		assertTrue(cooldowns.lastTrigger(EventScheduler.ANY_EVENT).isEmpty());
	}

	@Test
	void witnessGetsBothCooldownsWithoutRunningAnything() {
		MapCooldownTracker witness = new MapCooldownTracker();
		EventScheduler.record(EVENT, witness, 100);
		EventScheduler scheduler = rolling(0.0);

		assertEquals(Decision.ON_GLOBAL_COOLDOWN, scheduler.attempt("other_event", ALWAYS, 400, witness, 200, () -> true, triggers::incrementAndGet));
		assertEquals(Decision.ON_COOLDOWN, scheduler.attempt(EVENT, ALWAYS, 400, witness, 100 + 400, () -> true, triggers::incrementAndGet));
		assertEquals(0, triggers.get());
	}

	@Test
	void forceStartsGlobalCooldown() {
		EventScheduler scheduler = rolling(0.0);
		scheduler.force(EVENT, cooldowns, 100, triggers::incrementAndGet);

		assertEquals(Decision.ON_GLOBAL_COOLDOWN, scheduler.attempt("other_event", ALWAYS, 400, cooldowns, 101, () -> true, triggers::incrementAndGet));
	}

	@Test
	void globalKeyCannotBeAnEventId() {
		assertThrows(IllegalArgumentException.class, () -> new EventRegistry<String>(id -> id).register(EventScheduler.ANY_EVENT));
	}

	@Test
	void secondsAreConvertedToTicks() {
		assertEquals(new EventSettings(true, 12_000, 0.05), EventSettings.ofSeconds(true, 600, 0.05));
		assertEquals(604_800L * 20, EventSettings.ofSeconds(true, 604_800, 1.0).cooldownTicks());
	}
}
