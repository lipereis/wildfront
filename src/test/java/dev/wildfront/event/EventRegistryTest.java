package dev.wildfront.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class EventRegistryTest {
	private final EventRegistry<String> registry = new EventRegistry<>(Function.identity());

	@Test
	void keepsRegistrationOrder() {
		registry.register("second_event");
		registry.register("first_event");

		assertEquals(List.of("second_event", "first_event"), List.copyOf(registry.all()));
		assertEquals("first_event", registry.get("first_event").orElseThrow());
		assertTrue(registry.get("missing").isEmpty());
	}

	@Test
	void rejectsDuplicateIds() {
		registry.register("event");

		assertThrows(IllegalStateException.class, () -> registry.register("event"));
		assertEquals(1, registry.all().size());
	}

	@Test
	void rejectsInvalidIds() {
		assertThrows(IllegalArgumentException.class, () -> registry.register("Has Spaces"));
		assertThrows(IllegalArgumentException.class, () -> registry.register(""));
		assertThrows(IllegalArgumentException.class, () -> new EventRegistry<String>(entry -> null).register("x"));
	}
}
