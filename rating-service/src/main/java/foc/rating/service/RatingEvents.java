/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: builds rating.submitted from a stored rating, following
 * order-service's RequestEvents. correlationId = the request id and
 * parties = the ratee alone follow the #44 decisions and the record's
 * javadoc; no write-up in the payload (D32, IDs only).
 * 2026-11-04, Claude Code (Fable 5.1), notification D34 (author decision
 * via options Q&A): the parties argument removed — the ratee-only rule
 * now lives in the Notification Service. Author review: Leong Wei Zhi,
 * pending pull request review.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.contracts.events.rating.RatingSubmitted;
import foc.rating.entity.Rating;

import java.time.Instant;
import java.util.UUID;

/** The rating events this service publishes, built from a stored rating. */
final class RatingEvents {

    static final String PRODUCER = "rating-service";

    private RatingEvents() {
    }

    // rating.submitted: the Notification Service tells the ratee alone
    // (#44, notification D34); the write-up stays out of the payload (a
    // consumer reads it from this service)
    static RatingSubmitted submitted(Rating r) {
        return new RatingSubmitted(newEventId(), Instant.now(), PRODUCER, r.getRequestId(),
                r.getId().toString(), r.getRequestId(), r.getRaterId(),
                r.getRateeId(), (int) r.getStars());
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
