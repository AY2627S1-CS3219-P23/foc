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
 * 2026-10-17, Claude Code (Fable 5), issue #66: the display-name
 * fields (requesterName/courierName, D29) added earlier today were
 * removed again before merge — the author reopened D29 via options
 * Q&A and chose IDs-only events (docs/notification-service.md D30):
 * notification-service resolves names at ingest instead, keeping
 * display concerns out of the producers' contract.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A requester created a request; no courier is involved yet. Identity
 * {@code request.created} (see {@link EventTypeRegistry}).
 */
public record RequestCreated(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note) implements RequestEvent {
}
