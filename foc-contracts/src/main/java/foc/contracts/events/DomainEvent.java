/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed event contract base interface replacing the generic
 * EventEnvelope, per the author's Method-B decisions (flat events, no
 * envelope; derived canonical eventType; entity/sequence fields
 * dropped) recorded in docs/notification-service.md D16-D19.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Base contract every broker event implements: one flat record per
 * event type (see {@link EventTypeRegistry} for the catalog), carrying
 * these metadata accessors plus its own business fields at the top
 * level of the JSON body (D17). Events are past-tense facts that
 * already happened, never commands.
 *
 * <p>The canonical event identity is the registry string (e.g.
 * {@code "order.accepted"}). It travels twice by design: as the body's
 * {@code eventType} field (the contract's self-describing identity,
 * which consumers dispatch on — D11's body-only rule) and as the
 * RabbitMQ routing key (transport metadata). Both come from the same
 * {@link EventTypeRegistry} entry, so they cannot drift (D18).
 *
 * <p>{@code eventType} is deliberately <em>not</em> a record component:
 * it is derived from the class here and injected into the JSON by the
 * message converter, so an instance can never carry a mismatched type
 * string and the records need no validation boilerplate.
 *
 * <p>Contracts evolve additively only — add fields (marked
 * {@link Nullable} until every producer stamps them), never rename or
 * repurpose; consumers ignore unknown fields. A breaking change ships
 * as a <em>new event type</em> with its own identity string, record
 * and registry entry (see "Event conventions" in this library's
 * README) — decided 2026-09-20, removing the earlier schemaVersion
 * mechanism as unneeded complexity.
 */
public interface DomainEvent {

	/**
	 * Wire metadata field names common to every event; consumers use
	 * this to split metadata from business fields. {@code "eventType"}
	 * is derived ({@link #eventType()}) and injected by the message
	 * converter — never a record component.
	 */
	Set<String> METADATA_FIELDS = Set.of("eventId", "eventType",
			"occurredAt", "producer", "correlationId", "parties");

	/** Unique per event; duplicate detection (D4, F2.1). */
	String eventId();

	/** When the fact happened; ISO-8601 UTC on the wire. */
	Instant occurredAt();

	/** Publishing service, e.g. {@code "order-service"}. */
	String producer();

	/** Correlates the event with the request/flow that caused it. */
	String correlationId();

	/** User IDs to notify — one notification per entry (F1.1, F1.2). */
	List<String> parties();

	/**
	 * Canonical event identity — the registry's routing-key string
	 * (e.g. {@code "order.accepted"}). Derived from the class, so an
	 * instance can never carry a mismatched type (D18).
	 */
	default String eventType() {
		return EventTypeRegistry.routingKeyFor(getClass());
	}

}
