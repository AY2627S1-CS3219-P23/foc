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
 */
package foc.contracts.events.request;

import foc.contracts.events.core.EventTypeRegistry;

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
		String pickupLocation,
		String dropoffLocation,
		String note,
		Integer reward) implements RequestEvent {
}
