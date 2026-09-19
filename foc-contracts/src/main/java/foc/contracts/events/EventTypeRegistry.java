/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event-type registry per the author's Method-B decisions
 * (docs/notification-service.md D16-D19): plain-Java single source of
 * truth mapping each event class to its canonical identity string,
 * which serves as both the body eventType and the RabbitMQ routing
 * key. Same day, per the author's versioning decision on PR #75: the
 * catalog is keyed by (eventType, schemaVersion) with one record
 * class per version, so consumers can support several schema
 * versions of one event concurrently.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of every broker event: one entry per <em>(canonical
 * identity, schema version)</em> pair, each with its own record class
 * (D18). The identity string (e.g. {@code order.accepted}) serves as
 * both the JSON body's {@code eventType} (what consumers dispatch on,
 * together with {@code schemaVersion}) and the RabbitMQ routing key —
 * publisher and consumer read the same entry, so they can never
 * drift. All versions of an event share one identity/routing key;
 * the version selects the record class.
 *
 * <p>Adding a new event = one record class + one entry here + one
 * fixture. Adding a new <em>schema version</em> of an existing event
 * (a breaking change) = a new record class (e.g.
 * {@code OrderAcceptedV2}) + an entry with the same identity and the
 * bumped version + its fixture — the old version's entry stays until
 * every producer has migrated and dead-lettered backlog is replayed,
 * letting consumers accept both versions concurrently. Fixtures are
 * named {@code contracts/<identity dots&rarr;hyphens>-v<version>.example.json}
 * (see "Event conventions" in this library's README).
 *
 * <p>Plain Java on purpose: this library stays free of framework
 * dependencies (D15).
 */
public final class EventTypeRegistry {

	/**
	 * One catalog row: the record class for one (identity, schema
	 * version) pair. Consumers reject a version with no entry as a
	 * conversion failure (breaking changes bump the version; additive
	 * ones don't).
	 */
	public record Entry(Class<? extends DomainEvent> eventClass, String eventType, int schemaVersion) {
	}

	private static final List<Entry> ENTRIES = List.of(
			new Entry(OrderCreated.class, "order.created", 1),
			new Entry(OrderAccepted.class, "order.accepted", 1),
			new Entry(OrderCollected.class, "order.collected", 1),
			new Entry(OrderCompleted.class, "order.completed", 1),
			new Entry(OrderCancelled.class, "order.cancelled", 1),
			new Entry(OrderExpired.class, "order.expired", 1),
			new Entry(CourierArrived.class, "order.courier-arrived", 1));

	private static final Map<String, List<Entry>> BY_EVENT_TYPE = new HashMap<>();
	private static final Map<Class<?>, Entry> BY_CLASS = new HashMap<>();

	static {
		for (Entry entry : ENTRIES) {
			List<Entry> sameType = BY_EVENT_TYPE.computeIfAbsent(entry.eventType(), key -> new ArrayList<>());
			if (sameType.stream().anyMatch(other -> other.schemaVersion() == entry.schemaVersion())) {
				throw new IllegalStateException("duplicate (eventType, schemaVersion): "
						+ entry.eventType() + " v" + entry.schemaVersion());
			}
			sameType.add(entry);
			if (BY_CLASS.put(entry.eventClass(), entry) != null) {
				throw new IllegalStateException("duplicate event class: " + entry.eventClass());
			}
		}
		BY_EVENT_TYPE.replaceAll((key, sameType) -> List.copyOf(sameType));
	}

	/** All supported versions of this identity string; empty if unknown. */
	public static List<Entry> entriesFor(String eventType) {
		return BY_EVENT_TYPE.getOrDefault(eventType, List.of());
	}

	/** The catalog row for this (identity, schema version) pair, if supported. */
	public static Optional<Entry> entryFor(String eventType, int schemaVersion) {
		return entriesFor(eventType).stream()
				.filter(entry -> entry.schemaVersion() == schemaVersion)
				.findFirst();
	}

	/**
	 * The routing key (= canonical identity string) for this event
	 * class; all schema versions of an event share it.
	 *
	 * @throws IllegalArgumentException if the class is not registered
	 */
	public static String routingKeyFor(Class<?> eventClass) {
		Entry entry = BY_CLASS.get(eventClass);
		if (entry == null) {
			throw new IllegalArgumentException("unregistered event class: " + eventClass.getName());
		}
		return entry.eventType();
	}

	/** The whole catalog, unmodifiable — contract tests iterate this. */
	public static List<Entry> entries() {
		return ENTRIES;
	}

	private EventTypeRegistry() {
	}

}
