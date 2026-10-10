package dev.wildfront.event;

import java.util.List;
import java.util.Optional;

import dev.wildfront.config.EventConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Definition of one dynamic event. Implementations describe what the event needs and what it does;
 * when it runs is decided by {@link EventManager} and {@link EventScheduler}.
 */
public interface WorldEvent {
	/**
	 * One concrete occurrence of an event, with everything that had to be decided beforehand
	 * (such as where it happens) already settled.
	 */
	@FunctionalInterface
	interface Plan {
		/**
		 * @param participants everyone who experiences the occurrence: the triggering player first,
		 *                     followed by nearby players who share it
		 */
		void run(List<ServerPlayer> participants);
	}

	/**
	 * @return unique id made of lowercase letters, digits and underscores
	 */
	String id();

	/**
	 * @return the settings used when the config file does not override them
	 */
	EventConfig defaultConfig();

	/**
	 * Cheap requirements beyond the configurable ones (distance, time of day, weather), which the
	 * event manager checks itself. Called on the server thread. Must not load chunks or scan blocks.
	 */
	default boolean canTrigger(EventContext context) {
		return true;
	}

	/**
	 * Prepares an occurrence without changing the world. Called only after every other check
	 * passed, so it may do a bounded amount of searching, but must not load chunks.
	 *
	 * @return the occurrence, or empty if the event cannot happen here right now; in that case no
	 *         cooldown starts and the event is tried again on a later check
	 */
	Optional<Plan> plan(EventContext context);
}
