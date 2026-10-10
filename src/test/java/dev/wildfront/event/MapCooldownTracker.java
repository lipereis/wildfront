package dev.wildfront.event;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

final class MapCooldownTracker implements CooldownTracker {
	final Map<String, Long> times = new HashMap<>();

	@Override
	public OptionalLong lastTrigger(String eventId) {
		Long last = times.get(eventId);
		return last == null ? OptionalLong.empty() : OptionalLong.of(last);
	}

	@Override
	public void recordTrigger(String eventId, long gameTime) {
		times.put(eventId, gameTime);
	}
}
