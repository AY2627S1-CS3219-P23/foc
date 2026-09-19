/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event envelope DTO for issue #63 per the team-decided field
 * semantics in docs/notification-service.md ("Event envelope") and
 * decisions D4, D5, D11, D15. Placement in foc-contracts (rather than
 * per-service copies), the Map payload type, and the same-day revision
 * restricting envelope fields to event-handling semantics (adopting
 * the doc's platform-wide shape: entityType/entityId/parties
 * instead of orderId/requesterId/courierId) were author decisions.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The event envelope every broker event travels in — the contract
 * between producers (currently the Order Service) and consumers
 * (currently the Notification Service). The canonical example lives at
 * {@code /contracts/order-event.example.json} on this library's
 * classpath; both sides' contract tests assert against that fixture
 * (issue #63).
 *
 * <p>Every envelope field exists for a step of generic event handling
 * — dedupe, ordering, fan-out, display; nothing here is specific to
 * one business domain. All domain data (order IDs under their business
 * names, party roles, message details) rides in {@code payload}, and
 * everything rides in this JSON body — never in AMQP headers or other
 * broker-specific message properties (D11). The envelope evolves
 * additively only: add fields, never rename or repurpose, and
 * consumers must ignore unknown fields and event types (F1.3).
 *
 * <p>Deliberately a plain record with no Jackson annotations, keeping
 * this library free of framework dependencies (D15): Jackson binds
 * records by component name, and tolerant reading (unknown fields
 * ignored) is consumer-side {@code ObjectMapper} configuration —
 * Spring Boot's auto-configured mapper already behaves that way, and
 * the Notification Service's contract test locks it in.
 *
 * @param eventId       unique per event; duplicate detection (D4,
 *                      F2.1)
 * @param entityType    the kind of entity the event is about (e.g.
 *                      {@code "order"}); scopes {@code entityId} so
 *                      future producers publish through the same
 *                      envelope unchanged (Extensibility)
 * @param entityId      identifies the entity instance; groups events
 *                      for per-entity ordering (F2.4) — for order
 *                      events, the order ID
 * @param sequence      per-entity incrementing, stamped by the
 *                      producer; stale-event discard (D5, F2.4)
 * @param eventType     what happened — for orders, the six request
 *                      states (created / accepted / collected /
 *                      completed / cancelled / expired) plus
 *                      courier-arrived (Order F0.2, F4.1.1-F4.1.2);
 *                      an open set, so new types need no consumer or
 *                      publisher change (F1.3)
 * @param occurredAt    when the event happened, ISO-8601 UTC in JSON;
 *                      notification display and audit
 * @param parties       user IDs to notify — one notification per
 *                      entry, without querying other services (F1.1,
 *                      F1.2); role context, if a renderer needs it,
 *                      lives in {@code payload}
 * @param payload       free-form domain details for message text
 *                      rendering, generic per F1.3
 */
public record EventEnvelope(
		String eventId,
		String entityType,
		String entityId,
		long sequence,
		String eventType,
		Instant occurredAt,
		List<String> parties,
		Map<String, Object> payload) {
}
