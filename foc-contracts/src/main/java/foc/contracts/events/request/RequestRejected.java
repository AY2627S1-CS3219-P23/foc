/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-08.
 * Scope: typed request event record for the Order-Credit saga, written
 * from the team's design (docs/credit-service.md D7-D8). The identity
 * string and the fields are the team's decisions; the tool transcribed
 * them into the existing record pattern.
 * Author review: Ryan Ang, pending pull request review.
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
 * A submitted request was rejected because its reward could not be
 * reserved (saga step 3b); the request ends here. {@code reason} says
 * why. Identity {@code request.rejected} (see
 * {@link EventTypeRegistry}).
 */
public record RequestRejected(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note,
		String reason) implements RequestEvent {
}
