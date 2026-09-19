/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: registry catalog tests for the Method-B refactor
 * (docs/notification-service.md D16-D19).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventTypeRegistryTest {

	@Test
	void catalogsExactlySevenOrderEvents() {
		assertEquals(7, EventTypeRegistry.entries().size());
	}

	@Test
	void typeVersionPairsAndClassesAreUnique() {
		Set<String> typeVersions = new HashSet<>();
		Set<Class<?>> classes = new HashSet<>();
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(typeVersions.add(entry.eventType() + " v" + entry.schemaVersion()),
					"duplicate (eventType, schemaVersion): " + entry.eventType() + " v" + entry.schemaVersion());
			assertTrue(classes.add(entry.eventClass()), "duplicate class: " + entry.eventClass());
		}
	}

	@Test
	void allEventTypesBelongToTheOrderDomain() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(entry.eventType().startsWith("order."),
					entry.eventType() + " must start with \"order.\"");
		}
	}

	@Test
	void lookupsRoundTripForEveryEntry() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertEquals(entry,
					EventTypeRegistry.entryFor(entry.eventType(), entry.schemaVersion()).orElseThrow());
			assertTrue(EventTypeRegistry.entriesFor(entry.eventType()).contains(entry));
			assertEquals(entry.eventType(), EventTypeRegistry.routingKeyFor(entry.eventClass()));
		}
	}

	@Test
	void everyEntryDeclaresAPositiveSchemaVersion() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(entry.schemaVersion() >= 1,
					entry.eventType() + " must declare a positive supported schemaVersion");
		}
	}

	@Test
	void unknownEventTypeResolvesToEmpty() {
		assertTrue(EventTypeRegistry.entriesFor("order.refunded").isEmpty());
		assertTrue(EventTypeRegistry.entryFor("order.refunded", 1).isEmpty());
	}

	@Test
	void unsupportedVersionOfKnownTypeResolvesToEmpty() {
		assertTrue(EventTypeRegistry.entryFor("order.accepted", 99).isEmpty());
	}

	@Test
	void unregisteredClassIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> EventTypeRegistry.routingKeyFor(DomainEvent.class));
	}

}
