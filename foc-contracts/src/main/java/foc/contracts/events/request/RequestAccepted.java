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
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A courier accepted the request. Identity {@code request.accepted}
 * (see {@link EventTypeRegistry}).
 */
public record RequestAccepted(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		String courierId,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note) implements RequestEvent {
}
