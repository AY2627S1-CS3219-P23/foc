/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: typed order-lifecycle event record per the author's Method-B
 * decisions (docs/notification-service.md D16-D19); business fields
 * per the author's full-fixture-vocabulary decision.
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-09, Claude Code (Opus 5.5): note marked @Nullable (author's
 * decision, issue #165): a request may have no note, and a missing one
 * must not make consumers reject the event.
 * 2026-11-04, Claude Code (Fable 5.1), D34 (author decision via options
 * Q&A): parties removed; nullable courierId added so the courier who had
 * accepted stays reachable. Author review: Leong Wei Zhi, pending pull
 * request review.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;

/**
 * The deadline passed before collection. Identity {@code request.expired}
 * (see {@link EventTypeRegistry}). {@code courierId} is the courier who
 * had accepted, or null when the request expired while still open.
 */
public record RequestExpired(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		String requestId,
		String requesterId,
		@Nullable String courierId,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note) implements RequestEvent {
}
