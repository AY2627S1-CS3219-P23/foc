/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: GET /ratings/mine's wrapper. 200 with a null rating for a
 * party who has not rated yet (the normal pre-rating state the UI
 * renders), 404/403 reserved for ineligibility, mirroring POST — the
 * issue author's decision via options Q&A.
 * Author review: [pending]
 */
package foc.rating.dto;

/** The caller's own rating of one request; null until they rate. */
public record MyRatingResponse(RatingResponse rating) {
}
