/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report as the API returns it — the full row, since the
 * only readers are the reporter's 201 echo and the admin list.
 * Author review: [pending]
 */
package foc.rating.dto;

import java.time.Instant;

import foc.rating.entity.ReportReason;
import foc.rating.entity.UserReport;

/** One stored report; {@code details} and {@code requestId} are null when not given. */
public record ReportResponse(
        String id,
        String reportedUserId,
        String reporterId,
        String requestId,
        ReportReason reason,
        String details,
        Instant createdAt) {

    public static ReportResponse of(UserReport report) {
        return new ReportResponse(report.getId().toString(), report.getReportedUserId(),
                report.getReporterId(), report.getRequestId(), report.getReason(), report.getDetails(),
                report.getCreatedAt());
    }
}
