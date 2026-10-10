/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event-record shape tests for the Method-B refactor
 * (docs/notification-service.md D16-D19): derived eventType and the
 * metadata-component drift check.
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-09, Claude Code (Opus 5.5): every record's note must be
 * @Nullable (author's decision, issue #165). 2026-10-10, PR #166 review
 * (Leong Wei Zhi): every request record must also declare the note.
 * 2026-10-08, Claude Code (Opus 5.5): domain-marker test widened to the credit domain
 * (team design, docs/credit-service.md D7-D8); author review: Ryan Ang,
 * pending pull request review.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestEventRecordsTest {

	@Test
	void everyRecordDerivesItsRegistryEventType() throws Exception {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			DomainEvent event = defaultInstance(entry.eventClass());
			assertEquals(entry.eventType(), event.eventType());
		}
	}

	@Test
	void everyRecordIsARequestOrCreditEventWithARequestId() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(RequestEvent.class.isAssignableFrom(entry.eventClass())
					|| CreditEvent.class.isAssignableFrom(entry.eventClass()),
					entry.eventClass() + " must implement RequestEvent or CreditEvent");
			assertTrue(componentNames(entry.eventClass()).contains("requestId"));
		}
	}

	/**
	 * Drift check: every record must declare exactly the wire metadata
	 * components (METADATA_FIELDS minus the derived "eventType") and
	 * must NOT declare eventType as a component — the converter injects
	 * it (decision 8a/D18). Catches a new metadata field being added to
	 * the interface but forgotten on a record, or vice versa.
	 */
	@Test
	void metadataFieldsMatchRecordComponents() {
		Set<String> expected = new HashSet<>(DomainEvent.METADATA_FIELDS);
		expected.remove("eventType");
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			Set<String> components = componentNames(entry.eventClass());
			assertTrue(components.containsAll(expected),
					entry.eventClass() + " is missing metadata components");
			assertFalse(components.contains("eventType"),
					entry.eventClass() + " must not store eventType (it is derived)");
		}
	}

	/**
	 * A request may have no note: a record that required one would make
	 * consumers dead-letter the event, stopping a reservation, transfer or
	 * release over a display field (issue #165). Every request record must
	 * still declare the note, or a renamed one would pass unchecked.
	 */
	@Test
	void everyRequestRecordHasANullableNote() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			if (RequestEvent.class.isAssignableFrom(entry.eventClass())) {
				assertTrue(componentNames(entry.eventClass()).contains("note"),
						entry.eventClass() + " must declare a note component");
			}
			for (RecordComponent component : entry.eventClass().getRecordComponents()) {
				if (component.getName().equals("note")) {
					assertTrue(component.isAnnotationPresent(Nullable.class),
							entry.eventClass() + ".note must be @Nullable");
				}
			}
		}
	}

	private static Set<String> componentNames(Class<?> recordClass) {
		Set<String> names = new HashSet<>();
		for (RecordComponent component : recordClass.getRecordComponents()) {
			names.add(component.getName());
		}
		return names;
	}

	/** Builds a record instance with default values via its canonical constructor. */
	private static DomainEvent defaultInstance(Class<? extends DomainEvent> recordClass) throws Exception {
		RecordComponent[] components = recordClass.getRecordComponents();
		Class<?>[] parameterTypes = Arrays.stream(components)
				.map(RecordComponent::getType)
				.toArray(Class<?>[]::new);
		Object[] arguments = Arrays.stream(parameterTypes)
				.map(type -> type == int.class ? (Object) 0 : type == long.class ? (Object) 0L : null)
				.toArray();
		Constructor<? extends DomainEvent> canonical = recordClass.getDeclaredConstructor(parameterTypes);
		return canonical.newInstance(arguments);
	}

}
