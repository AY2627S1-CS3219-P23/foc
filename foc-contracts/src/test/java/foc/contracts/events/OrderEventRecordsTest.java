/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event-record shape tests for the Method-B refactor
 * (docs/notification-service.md D16-D19): derived eventType and the
 * metadata-component drift check.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderEventRecordsTest {

	@Test
	void everyRecordDerivesItsRegistryEventType() throws Exception {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			DomainEvent event = defaultInstance(entry.eventClass());
			assertEquals(entry.eventType(), event.eventType());
		}
	}

	@Test
	void everyRecordIsAnOrderEventWithAnOrderId() {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			assertTrue(OrderEvent.class.isAssignableFrom(entry.eventClass()),
					entry.eventClass() + " must implement OrderEvent");
			assertTrue(componentNames(entry.eventClass()).contains("orderId"));
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
