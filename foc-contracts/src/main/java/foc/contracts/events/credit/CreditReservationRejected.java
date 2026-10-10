/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-08.
 * Scope: typed credit event record for the Order-Credit saga, written
 * from the team's design (docs/credit-service.md D7-D8). The identity
 * string and the fields are the team's decisions; the tool transcribed
 * them into the existing record pattern.
 * 2026-10-09, PR #163 Copilot review: amount changed from int to Integer
 * (team decision) so a missing value is null and consumers reject it.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.contracts.events.credit;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;
import java.util.List;

/**
 * The Credit Service refused to reserve a request's reward (saga step
 * 2); nothing was held. {@code reason} says why. Identity
 * {@code credit.reservation-rejected} (see {@link EventTypeRegistry}).
 */
public record CreditReservationRejected(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String requestId,
		String requesterId,
		Integer amount,
		String reason) implements CreditEvent {
}
