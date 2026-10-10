package dev.wildfront.event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Ordered collection of entries keyed by a unique id. Registration order is iteration order.
 */
public final class EventRegistry<T> {
	private final Map<String, T> entries = new LinkedHashMap<>();
	private final Function<T, String> idOf;

	public EventRegistry(Function<T, String> idOf) {
		this.idOf = idOf;
	}

	public T register(T entry) {
		String id = idOf.apply(entry);

		if (id == null || !id.matches("[a-z0-9_]+")) {
			throw new IllegalArgumentException("Invalid event id '" + id + "': use lowercase letters, digits and underscores");
		}

		if (entries.putIfAbsent(id, entry) != null) {
			throw new IllegalStateException("Event id '" + id + "' is already registered");
		}

		return entry;
	}

	public Optional<T> get(String id) {
		return Optional.ofNullable(entries.get(id));
	}

	public Collection<T> all() {
		return Collections.unmodifiableCollection(entries.values());
	}

	public Collection<String> ids() {
		return Collections.unmodifiableSet(entries.keySet());
	}
}
