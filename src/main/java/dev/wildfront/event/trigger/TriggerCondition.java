package dev.wildfront.event.trigger;

import java.util.List;
import java.util.function.Predicate;

import dev.wildfront.event.EventContext;

/**
 * One named requirement for an event to trigger. The name is shown to operators when the
 * requirement is not met, so it should read as a short description of what is needed.
 */
public interface TriggerCondition {
	String name();

	/**
	 * Called on the server thread. Must not load chunks or scan blocks.
	 */
	boolean test(EventContext context);

	static TriggerCondition of(String name, Predicate<EventContext> predicate) {
		return new TriggerCondition() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean test(EventContext context) {
				return predicate.test(context);
			}
		};
	}

	/**
	 * Stops at the first condition that fails.
	 */
	static boolean allMet(List<TriggerCondition> conditions, EventContext context) {
		for (TriggerCondition condition : conditions) {
			if (!condition.test(context)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * @return the names of every condition that currently fails, in order
	 */
	static List<String> unmet(List<TriggerCondition> conditions, EventContext context) {
		return conditions.stream().filter(condition -> !condition.test(context)).map(TriggerCondition::name).toList();
	}
}
