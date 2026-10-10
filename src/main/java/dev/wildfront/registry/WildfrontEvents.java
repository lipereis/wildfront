package dev.wildfront.registry;

import java.util.Collection;
import java.util.Optional;

import dev.wildfront.Wildfront;
import dev.wildfront.event.EventRegistry;
import dev.wildfront.event.WorldEvent;
import dev.wildfront.event.type.MysteriousDiscoveryEvent;

/**
 * All dynamic events known to Wildfront. New events are added here and nowhere else.
 */
public final class WildfrontEvents {
	private static final EventRegistry<WorldEvent> REGISTRY = new EventRegistry<>(WorldEvent::id);

	public static final WorldEvent MYSTERIOUS_DISCOVERY = REGISTRY.register(new MysteriousDiscoveryEvent());

	private WildfrontEvents() {
	}

	public static void init() {
		Wildfront.LOGGER.info("[Wildfront] Registered {} event(s): {}", REGISTRY.ids().size(), REGISTRY.ids());
	}

	public static Optional<WorldEvent> get(String id) {
		return REGISTRY.get(id);
	}

	public static Collection<WorldEvent> all() {
		return REGISTRY.all();
	}

	public static Collection<String> ids() {
		return REGISTRY.ids();
	}
}
