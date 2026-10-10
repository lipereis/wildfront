package dev.wildfront.event;

import dev.wildfront.config.WildfrontConfig;

/**
 * Definition of one dynamic event. Implementations describe what the event needs and what it does;
 * when it runs is decided by {@link EventManager} and {@link EventScheduler}.
 */
public interface WorldEvent {
	/**
	 * @return unique id made of lowercase letters, digits and underscores
	 */
	String id();

	EventSettings settings(WildfrontConfig config);

	/**
	 * Event-specific conditions. Called only after the cooldown and chance checks passed, on the
	 * server thread. Must not load chunks or scan blocks.
	 */
	boolean canTrigger(EventContext context);

	void trigger(EventContext context);
}
