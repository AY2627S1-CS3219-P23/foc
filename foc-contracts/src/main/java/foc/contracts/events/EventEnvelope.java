/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: event envelope DTO for issue #63 per the team-decided field
 * list in docs/notification-service.md ("Event envelope") and
 * decisions D4, D5, D11, D15. Placement in foc-contracts (rather than
 * per-service copies) and the Map payload type were author decisions.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.time.Instant;
import java.util.Map;

/**
 * The event envelope every order event travels in — the contract
 * between the Order Service (producer) and the Notification Service
 * (consumer). The canonical example lives at
 * {@code /contracts/order-event.json} on this library's classpath;
 * both sides' contract tests assert against that fixture (issue #63).
 *
 * <p>All business data rides in this JSON body — never in AMQP headers
 * or other broker-specific message properties (D11). The envelope
 * evolves additively only: add fields, never rename or repurpose, and
 * consumers must ignore unknown fields and event types (F1.3).
 *
 * <p>Deliberately a plain record with no Jackson annotations, keeping
 * this library free of framework dependencies (D15): Jackson binds
 * records by component name, and tolerant reading (unknown fields
 * ignored) is consumer-side {@code ObjectMapper} configuration —
 * Spring Boot's auto-configured mapper already behaves that way, and
 * the Notification Service's contract test locks it in.
 *
 * @param eventId     unique per event; duplicate detection (D4, F2.1)
 * @param orderId     groups events per order (F2.4)
 * @param sequence    per-order incrementing, stamped by the Order
 *                    Service; stale-event discard (D5, F2.4)
 * @param type        the six request states — created / accepted /
 *                    collected / completed / cancelled / expired —
 *                    plus courier-arrived (Order F0.2, F4.1.1-F4.1.2);
 *                    an open set, so new types need no consumer or
 *                    publisher change (F1.3)
 * @param occurredAt  when the event happened, ISO-8601 UTC in JSON;
 *                    notification display and audit
 * @param requesterId requester user ID, so each party is notified
 *                    without querying other services (F1.1, F1.2)
 * @param courierId   courier user ID; null while no courier is
 *                    associated (e.g. a created event)
 * @param payload     free-form details for message text rendering,
 *                    generic per F1.3
 */
public record EventEnvelope(
		String eventId,
		String orderId,
		long sequence,
		String type,
		Instant occurredAt,
		String requesterId,
		String courierId,
		Map<String, Object> payload) {
}
