/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-21.
 * Scope: typed event record for N3 dispute resolution (issue #234),
 * written from the team's decisions (DSP-8, DSP-9, DSP-12): the identity
 * string, the fields and their nullability are the team's; the tool
 * transcribed them into the existing record pattern.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * An admin resolved a disputed request by dividing its held reward:
 * {@code courierAmount} goes to the courier and {@code requesterAmount}
 * back to the requester. Both are 0 or more and add up to the reward.
 * Identity {@code request.dispute-resolved} (see
 * {@link EventTypeRegistry}).
 *
 * <p>{@code note} is the request's own note, as on every request event;
 * the admin's explanation is {@code resolutionNote}.
 */
public record RequestDisputeResolved(
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
		@Nullable String note,
		Integer courierAmount,
		Integer requesterAmount,
		@Nullable String resolutionNote) implements RequestEvent {
}
