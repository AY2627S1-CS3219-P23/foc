/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: registry catalog tests for the Method-B refactor
 * (docs/notification-service.md D16-D19).
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-08, Claude Code (Opus 5.5): count and domain-prefix tests updated for the credit domain
 * (team design, docs/credit-service.md D7-D8); author review: Ryan Ang,
 * pending pull request review.
 */
package foc.contracts.events.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.request.RequestEvent;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventTypeRegistryTest {

	@Test
	void catalogsExactlyElevenEvents() {
		assertEquals(11, EventTypeRegistry.entries().size());
	}

	@Test
	void eventTypesAndClassesAreUnique() {
		Set<String> types = new HashSet<>();
		Set<Class<?>> classes = new HashSet<>();
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(types.add(entry.eventType()), "duplicate eventType: " + entry.eventType());
			assertTrue(classes.add(entry.eventClass()), "duplicate class: " + entry.eventClass());
		}
	}

	@Test
	void everyEventTypeIsPrefixedWithItsDomain() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			String prefix = domainPrefix(entry.eventClass());
			assertTrue(entry.eventType().startsWith(prefix),
					entry.eventType() + " must start with \"" + prefix + "\"");
		}
	}

	@Test
	void lookupsRoundTripForEveryEntry() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertEquals(entry, EventTypeRegistry.entryFor(entry.eventType()).orElseThrow());
			assertEquals(entry.eventType(), EventTypeRegistry.routingKeyFor(entry.eventClass()));
		}
	}

	@Test
	void unknownEventTypeResolvesToEmpty() {
		assertTrue(EventTypeRegistry.entryFor("request.refunded").isEmpty());
	}

	@Test
	void unregisteredClassIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> EventTypeRegistry.routingKeyFor(DomainEvent.class));
	}

	private static String domainPrefix(Class<?> eventClass) {
		if (RequestEvent.class.isAssignableFrom(eventClass)) {
			return "request.";
		}
		if (CreditEvent.class.isAssignableFrom(eventClass)) {
			return "credit.";
		}
		throw new AssertionError(eventClass + " implements no domain marker interface");
	}

}
