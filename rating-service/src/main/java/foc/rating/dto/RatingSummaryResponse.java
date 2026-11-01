/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: the profile's star-row aggregate. Null average for a user with
 * no ratings (not 0, which reads as a terrible score) and the raw
 * average with display rounding left to the UI — both the author's
 * decisions via options Q&A.
 * Author review: [pending]
 */
package foc.rating.dto;

/**
 * A user's rating aggregate ("4.2 (18 reviews)"). {@code average} is
 * null when {@code count} is 0; the UI rounds for display (#257).
 */
public record RatingSummaryResponse(Double average, long count) {
}
