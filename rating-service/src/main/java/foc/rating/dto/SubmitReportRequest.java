/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report body from the issue (reason from the fixed list,
 * details and requestId optional), SubmitRatingRequest's style. The
 * 255-char caps on the ids keep an oversized value a 400, not the
 * varchar(255) columns' 500 — the author's decision; the 1000-char
 * details cap mirrors the rating write-up's.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #274 review (Sinnez1): the NUL
 * patterns — PostgreSQL rejects zero bytes, which would be a 500.
 * Author review: [pending]
 */
package foc.rating.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import foc.rating.entity.ReportReason;

/** POST /reports: report a user for irresponsible behaviour. */
public record SubmitReportRequest(

        // NUL is refused here because PostgreSQL rejects zero bytes in
        // text parameters, which would surface as a 500 (PR #274 review)
        @NotBlank(message = "reportedUserId is required")
        @Size(max = 255, message = "reportedUserId must be at most 255 characters")
        @Pattern(regexp = NO_NUL, message = "reportedUserId must not contain the NUL character")
        String reportedUserId,

        // an unknown reason never reaches @NotNull: Jackson refuses it as
        // an unreadable body, which is the same 400
        @NotNull(message = "reason is required")
        ReportReason reason,

        @Size(max = DETAILS_MAX, message = "details must be at most " + DETAILS_MAX + " characters")
        @Pattern(regexp = NO_NUL, message = "details must not contain the NUL character")
        String details,

        @Size(max = 255, message = "requestId must be at most 255 characters")
        @Pattern(regexp = NO_NUL, message = "requestId must not contain the NUL character")
        String requestId) {

    // any string without a zero byte; @Pattern skips null values, so the
    // optional fields stay optional
    private static final String NO_NUL = "[^\\x00]*";

    /** The write-up cap; the UI's counter should match. */
    public static final int DETAILS_MAX = 1000;
}
