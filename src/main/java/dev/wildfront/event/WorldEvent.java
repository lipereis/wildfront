package dev.wildfront.event;

import dev.wildfront.config.EventConfig;

/**
 * Definition of one dynamic event. Implementations describe what the event needs and what it does;
 * when it runs is decided by {@link EventManager} and {@link EventScheduler}.
 */
public interface WorldEvent {
	/**
	 * @return unique id made of lowercase letters, digits and underscores
	 */
	String id();

	/**
	 * @return the settings used when the config file does not override them
	 */
	EventConfig defaultConfig();

	/**
	 * Requirements beyond the configurable ones (distance, time of day, weather), which the event
	 * manager checks itself. Called on the server thread. Must not load chunks or scan blocks.
	 */
	default boolean canTrigger(EventContext context) {
		return true;
	}

	/**
	 * Runs the event once for all {@link EventContext#participants()}.
	 */
	void trigger(EventContext context);
}
