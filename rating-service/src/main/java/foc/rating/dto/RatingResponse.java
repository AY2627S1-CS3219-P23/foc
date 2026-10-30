/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the rating as the API returns it; field names mirror the
 * submit body (text, not the column's body), the author's decision.
 * Author review: [pending]
 */
package foc.rating.dto;

import java.time.Instant;

import foc.rating.entity.Rating;

/** One stored rating; {@code text} is null when the rater left none. */
public record RatingResponse(
        String id,
        String requestId,
        String raterId,
        String rateeId,
        int stars,
        String text,
        Instant createdAt) {

    public static RatingResponse of(Rating rating) {
        return new RatingResponse(rating.getId().toString(), rating.getRequestId(), rating.getRaterId(),
                rating.getRateeId(), rating.getStars(), rating.getBody(), rating.getCreatedAt());
    }
}
