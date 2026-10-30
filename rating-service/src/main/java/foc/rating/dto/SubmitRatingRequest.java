/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the submit body from the issue (stars 1-5 required, text
 * optional), user-service's validated-record style. stars is Integer so
 * a missing value fails @NotNull instead of reading 0 (the PR #163
 * convention); the 1000-character cap is the issue author's decision
 * (wireframe gives no number; the column is unbounded, so the cap can
 * change without a migration).
 * Author review: [pending]
 */
package foc.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /ratings: rate the other party of a completed request. */
public record SubmitRatingRequest(

        @NotBlank(message = "requestId is required")
        String requestId,

        @NotNull(message = "stars is required")
        @Min(value = 1, message = "stars must be between 1 and 5")
        @Max(value = 5, message = "stars must be between 1 and 5")
        Integer stars,

        @Size(max = TEXT_MAX, message = "text must be at most " + TEXT_MAX + " characters")
        String text) {

    /** The write-up cap; the UI's counter should match. */
    public static final int TEXT_MAX = 1000;
}
