/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5), 2026-10-28.
 * Scope: typed rating event record for the N1 rating system (issue
 * #249), written from the issue author's decisions recorded on #44:
 * the ratee is notified, stars are required, and the identity string
 * and field list are the author's; the tool transcribed them into the
 * existing record pattern. stars is Integer, not int, following the
 * PR #163 review decision on CreditReserved.amount — a missing value
 * is null and the consumer rejects it rather than reading 0.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.contracts.events.rating;

import foc.contracts.events.core.EventTypeRegistry;

import java.time.Instant;
import java.util.List;

/**
 * A party rated the other party for a completed request. Identity
 * {@code rating.submitted} (see {@link EventTypeRegistry}).
 *
 * <p>{@code parties} holds the <em>ratee</em> alone: the rated party is
 * told a review landed, the rater is not notified of their own action
 * (decisions on issue #44).
 *
 * <p>The reviewer's write-up is deliberately absent. Payloads carry IDs
 * only (D32), so a consumer that wants the text reads it from the
 * Rating Service; a rating with no write-up is valid, and {@code note}
 * on these contracts always means the request's own note.
 */
public record RatingSubmitted(
		String eventId,
		Instant occurredAt,
		String producer,
		String correlationId,
		List<String> parties,
		String ratingId,
		String requestId,
		String raterId,
		String rateeId,
		Integer stars) implements RatingEvent {
}
