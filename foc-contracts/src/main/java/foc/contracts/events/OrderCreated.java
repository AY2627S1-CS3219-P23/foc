/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed order-lifecycle event record per the author's Method-B
 * decisions (docs/notification-service.md D16-D19); business fields
 * per the author's full-fixture-vocabulary decision.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.time.Instant;
import java.util.List;

/**
 * A requester created an order; no courier is involved yet. Identity
 * {@code order.created} (see {@link EventTypeRegistry}).
 */
public record OrderCreated(
		String eventId,
		int schemaVersion,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String orderId,
		String requesterId,
		String pickupLocation,
		String dropoffLocation,
		String note) implements OrderEvent {
}
