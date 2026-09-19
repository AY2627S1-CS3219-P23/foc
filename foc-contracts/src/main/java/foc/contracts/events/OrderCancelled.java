/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed order-lifecycle event record per the author's Method-B
 * decisions (docs/notification-service.md D16-D19); business fields
 * per the author's full-fixture-vocabulary decision (nullable
 * courierId here is the author's schema decision).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.time.Instant;
import java.util.List;

/**
 * The order was cancelled. Identity {@code order.cancelled}
 * (see {@link EventTypeRegistry}).
 *
 * @param courierId {@code null} when the order was cancelled before a
 *                  courier accepted it
 */
public record OrderCancelled(
		String eventId,
		int schemaVersion,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String orderId,
		String requesterId,
		String courierId,
		String pickupLocation,
		String dropoffLocation,
		String note) implements OrderEvent {
}
