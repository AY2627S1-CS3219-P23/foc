/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event-type registry per the author's Method-B decisions
 * (docs/notification-service.md D16-D19): plain-Java single source of
 * truth mapping each event class to its canonical identity string,
 * which serves as both the body eventType and the RabbitMQ routing
 * key.
 * 2026-09-20: the author decided to remove the schemaVersion
 * mechanism (added on PR #75 review) as unneeded standing complexity;
 * the catalog is keyed by identity alone again, and a breaking change
 * ships as a new event type.
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events.core;

import foc.contracts.events.request.CourierArrived;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCollected;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestCreated;
import foc.contracts.events.request.RequestExpired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of every broker event: one entry per record class,
 * pairing it with its canonical identity string, e.g.
 * {@code request.accepted} (D18). That one string serves as both the
 * JSON body's {@code eventType} (what consumers dispatch on) and the
 * RabbitMQ routing key — publisher and consumer read the same entry,
 * so the two can never drift.
 *
 * <p>Adding a new event = one record class + one entry here + one
 * {@code contracts/<identity dots&rarr;hyphens>.example.json} fixture;
 * the registry-driven contract tests pick it up automatically. A
 * <em>breaking</em> change to an existing event ships the same way,
 * as a new event type with its own identity string (e.g.
 * {@code request.accepted.v2}); consumers reject unknown types, which
 * covers the migration window (see "Event conventions" in this
 * library's README).
 *
 * <p>Plain Java on purpose: this library stays free of framework
 * dependencies (D15).
 */
public final class EventTypeRegistry {

	/** One catalog row: the event class and its canonical identity string. */
	public record Entry(Class<? extends DomainEvent> eventClass, String eventType) {
	}

	private static final List<Entry> ENTRIES = List.of(
			new Entry(RequestCreated.class, "request.created"),
			new Entry(RequestAccepted.class, "request.accepted"),
			new Entry(RequestCollected.class, "request.collected"),
			new Entry(RequestCompleted.class, "request.completed"),
			new Entry(RequestCancelled.class, "request.cancelled"),
			new Entry(RequestExpired.class, "request.expired"),
			new Entry(CourierArrived.class, "request.courier-arrived"));

	private static final Map<String, Entry> BY_EVENT_TYPE = new HashMap<>();
	private static final Map<Class<?>, Entry> BY_CLASS = new HashMap<>();

	static {
		for (Entry entry : ENTRIES) {
			if (BY_EVENT_TYPE.put(entry.eventType(), entry) != null) {
				throw new IllegalStateException("duplicate eventType: " + entry.eventType());
			}
			if (BY_CLASS.put(entry.eventClass(), entry) != null) {
				throw new IllegalStateException("duplicate event class: " + entry.eventClass());
			}
		}
	}

	/** The catalog row registered for this identity string, if any. */
	public static Optional<Entry> entryFor(String eventType) {
		return Optional.ofNullable(BY_EVENT_TYPE.get(eventType));
	}

	/**
	 * The routing key (= canonical identity string) for this event
	 * class.
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
