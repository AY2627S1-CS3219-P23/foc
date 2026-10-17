/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-08.
 * Scope: typed request event record for the Order-Credit saga, written
 * from the team's design (docs/credit-service.md D7-D8). The identity
 * string and the fields are the team's decisions; the tool transcribed
 * them into the existing record pattern.
 * 2026-10-09, PR #163 Copilot review: reward changed from int to Integer
 * (team decision) so a missing value is null and consumers reject it.
 * Author review: Ryan Ang, pending pull request review.
 * 2026-10-09, Claude Code (Opus 5.5): note marked @Nullable (author's
 * decision, issue #165): a request may have no note, and a missing one
 * must not make consumers reject the event.
 * 2026-10-17, Claude Code (Fable 5), issue #66: display-name fields
 * added beside the party IDs (requesterName/courierName, author
 * decision via options Q&A, docs/notification-service.md D29) so
 * notification copy can show who acted without a cross-service
 * lookup. @Nullable for the same reason as note (issue #165): a
 * missing display field must never make a consumer reject the event.
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.core.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A requester submitted a request and it is waiting for its reward to
 * be reserved (saga step 1); it is not visible to couriers yet.
 * {@code reward} is the amount the Credit Service reserves. Identity
 * {@code request.submitted} (see {@link EventTypeRegistry}).
 */
public record RequestSubmitted(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		@Nullable String requesterName,
		String pickupLocation,
		String dropoffLocation,
		@Nullable String note,
		Integer reward) implements RequestEvent {
}
