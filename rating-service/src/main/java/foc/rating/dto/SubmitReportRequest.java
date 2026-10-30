/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report body from the issue (reason from the fixed list,
 * details and requestId optional), SubmitRatingRequest's style. The
 * 255-char caps on the ids keep an oversized value a 400, not the
 * varchar(255) columns' 500 — the author's decision; the 1000-char
 * details cap mirrors the rating write-up's.
 * Author review: [pending]
 */
package foc.rating.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import foc.rating.entity.ReportReason;

/** POST /reports: report a user for irresponsible behaviour. */
public record SubmitReportRequest(

        @NotBlank(message = "reportedUserId is required")
        @Size(max = 255, message = "reportedUserId must be at most 255 characters")
        String reportedUserId,

        // an unknown reason never reaches @NotNull: Jackson refuses it as
        // an unreadable body, which is the same 400
        @NotNull(message = "reason is required")
        ReportReason reason,

        @Size(max = DETAILS_MAX, message = "details must be at most " + DETAILS_MAX + " characters")
        String details,

        @Size(max = 255, message = "requestId must be at most 255 characters")
        String requestId) {

    /** The write-up cap; the UI's counter should match. */
    public static final int DETAILS_MAX = 1000;
}
