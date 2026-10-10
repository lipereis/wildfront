package dev.wildfront.event;

import java.util.OptionalLong;

/**
 * Remembers when each event last triggered for one subject (in practice, one player).
 */
public interface CooldownTracker {
	/**
	 * @return the game time of the last trigger of the event, or empty if it never triggered
	 */
	OptionalLong lastTrigger(String eventId);

	void recordTrigger(String eventId, long gameTime);
}
