/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: one review as the public profile shows it — the issue's field
 * set, deliberately narrower than RatingResponse: no rating id, request
 * id or ratee id leaves this service's door.
 * Author review: [pending]
 */
package foc.rating.dto;

import java.time.Instant;

import foc.rating.entity.Rating;

/**
 * One review in a user's public history. Only the rater's id crosses
 * the API; the web hydrates usernames at read time (D32).
 */
public record ReviewResponse(
        String raterId,
        int stars,
        String text,
        Instant createdAt) {

    public static ReviewResponse of(Rating rating) {
        return new ReviewResponse(rating.getRaterId(), rating.getStars(), rating.getBody(),
                rating.getCreatedAt());
    }
}
