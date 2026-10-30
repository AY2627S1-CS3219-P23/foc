/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: filing and listing reports per the issue (400 self-report,
 * newest-first admin page), RatingService's style. No existence check on
 * the reported user or the request — rating-service has no user table
 * and request_id is opaque (the author's decisions via options Q&A).
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #274 review (claude[bot]):
 * reportedUserId trimmed before the self-check and the save — @NotBlank
 * admits padding, which dodged the 400 and stored an id matching no
 * user.
 * Author review: [pending]
 */
package foc.rating.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.rating.entity.ReportReason;
import foc.rating.entity.UserReport;
import foc.rating.repository.UserReportRepository;

/**
 * Reporting a user for irresponsible behaviour (N1.4): stored and
 * listed only. Admin actioning (ban/suspend) is out of N1's scope, and
 * no event leaves this service — a report is a moderation flag, not an
 * N3 dispute.
 */
@Service
public class ReportService {

    private final UserReportRepository reports;

    public ReportService(UserReportRepository reports) {
        this.reports = reports;
    }

    /** Stores the caller's report of another user; reporting oneself is refused. */
    @Transactional
    public UserReport file(String reporterId, String reportedUserId, ReportReason reason, String details,
            String requestId) {
        // @NotBlank admits padding: trimmed, or " <my-id>" dodges the
        // self-check and stores an id that matches no user
        String reported = reportedUserId.trim();
        if (reporterId.equals(reported)) {
            throw new SelfReportException();
        }
        return reports.save(new UserReport(reported, reporterId, normalized(requestId), reason,
                normalized(details), Instant.now()));
    }

    /** A page of all reports for the admin list, newest first. */
    @Transactional(readOnly = true)
    public Page<UserReport> list(int page, int size) {
        // the id breaks ties so pages never overlap (order-service precedent)
        return reports.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    // a value of only whitespace is no value; the column stays null
    private static String normalized(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
