/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report row the issue specifies, mirroring the V5 migration
 * and Rating's shape (generated UUID id, no setters). request_id held
 * as an opaque string, not a rateable_request reference — the author's
 * decision via options Q&A.
 * Author review: [pending]
 */
package foc.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One user's report of another for irresponsible behaviour (N1.4): a
 * moderation flag, not an N3 dispute. The request id, when given, is
 * unverified context for the admin reading the list — rating-service
 * only ever hears about completed requests, and a report is often about
 * one that never completed. Immutable, so there are no setters.
 */
@Entity
@Table(name = "user_report")
public class UserReport {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "reported_user_id", nullable = false, updatable = false)
    private String reportedUserId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private String reporterId;

    // optional, opaque: where it happened, by the reporter's account
    @Column(name = "request_id", updatable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReportReason reason;

    // the optional write-up; null when the reporter left none
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(updatable = false, columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserReport() {
    }

    public UserReport(String reportedUserId, String reporterId, String requestId, ReportReason reason,
            String details, Instant createdAt) {
        this.reportedUserId = reportedUserId;
        this.reporterId = reporterId;
        this.requestId = requestId;
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getReportedUserId() {
        return reportedUserId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getRequestId() {
        return requestId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
