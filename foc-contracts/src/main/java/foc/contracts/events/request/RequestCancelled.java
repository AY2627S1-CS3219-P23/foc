/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed order-lifecycle event record per the author's Method-B
 * decisions (docs/notification-service.md D16-D19); business fields
 * per the author's full-fixture-vocabulary decision (nullable
 * courierId here is the author's schema decision).
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The request was cancelled. Identity {@code request.cancelled}
 * (see {@link EventTypeRegistry}).
 *
 * @param courierId {@code null} when the request was cancelled before a
 *                  courier accepted it
 */
public record RequestCancelled(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		@Nullable String courierId,
		String pickupLocation,
		String dropoffLocation,
		String note) implements RequestEvent {
}
