/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: the eligibility row the issue specifies — request_id PK,
 * the two parties, completed_at — mirroring the V2 migration.
 * completed_at carries the event's occurredAt: RequestCompleted has
 * no separate completion timestamp.
 * Author review: [pending]
 */
package foc.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per completed request: who may rate whom (N1.1) and when the
 * request completed. Rows come only from consumed
 * {@code request.completed} events; #252's submit API reads them to
 * decide eligibility.
 */
@Entity
@Table(name = "rateable_request")
public class RateableRequest {

    @Id
    @Column(name = "request_id")
    private String requestId;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "courier_id", nullable = false)
    private String courierId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected RateableRequest() {
    }

    public RateableRequest(String requestId, String requesterId, String courierId, Instant completedAt) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.courierId = courierId;
        this.completedAt = completedAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getCourierId() {
        return courierId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
