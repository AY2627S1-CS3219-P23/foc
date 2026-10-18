/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-18.
 * Scope: typed event record for the courier-withdrawal transition,
 * mirroring RequestAccepted's component set. A distinct type instead
 * of request.cancelled per docs/credit-service.md (the reward stays
 * held, Order F5.2); event name and requester-only parties are the
 * author's decisions (via options Q&A).
 * Reviewed by: pending pull request review.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The courier withdrew after accepting, and the request returned to
 * the open feed. Identity {@code request.dropped}
 * (see {@link EventTypeRegistry}).
 *
 * <p>Not a {@code request.cancelled}: the reward stays reserved for
 * the next courier (Order F5.2), so the Credit Service must never
 * consume this event. {@code courierId} is the courier who dropped —
 * always present, a drop can only happen after an accept.
 */
public record RequestDropped(
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
