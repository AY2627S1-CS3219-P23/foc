/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: JPA entity for the request_dispute table of team
 * decision DSP-2 (Flyway's V3), following Requests. The columns, their
 * types and nullability are the team's; the request is held as its id,
 * not as an association, so reading a dispute never loads its request.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order.model;

import foc.order.DisputeCategory;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "request_dispute")
public class RequestDispute implements Persistable<Long> {

    // The disputed request's id: one dispute per request
    @Id
    private Long requestId;

    // Stored by name; the allowed names are a CHECK in Flyway's V3
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private DisputeCategory category;

    // The requester's text, at most 500 characters (DSP-7)
    @Column(columnDefinition = "text")
    private String disputeNote;

    // The requester's id; null when the sweep escalated it
    private Long raisedBy;

    @Column(nullable = false)
    private Instant raisedAt;

    @Column(nullable = false)
    private boolean autoEscalated;

    // The resolution: all null until an admin resolves the dispute
    private Long resolvedBy;

    private Instant resolvedAt;

    private Integer courierAmount;

    private Integer requesterAmount;

    @Column(columnDefinition = "text")
    private String resolutionNote;

    // The id is assigned, not generated, so Spring Data cannot tell a new
    // dispute from a stored one by it: without this, save would merge
    // (read, then insert or overwrite) instead of insert.
    @Transient
    private boolean isNew = true;

    protected RequestDispute() {
    }

    public RequestDispute(Long requestId, DisputeCategory category, String disputeNote, Long raisedBy,
            Instant raisedAt, boolean autoEscalated) {
        this.requestId = requestId;
        this.category = category;
        this.disputeNote = disputeNote;
        this.raisedBy = raisedBy;
        this.raisedAt = raisedAt;
        this.autoEscalated = autoEscalated;
    }

    // Records the admin's division of the reward.
    public void resolve(Long resolvedBy, Instant resolvedAt, Integer courierAmount, Integer requesterAmount,
            String resolutionNote) {
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.courierAmount = courierAmount;
        this.requesterAmount = requesterAmount;
        this.resolutionNote = resolutionNote;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    @Override
    public Long getId() {
        return requestId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    // Getters

    public Long getRequestId() {
        return requestId;
    }

    public DisputeCategory getCategory() {
        return category;
    }

    public String getDisputeNote() {
        return disputeNote;
    }

    public Long getRaisedBy() {
        return raisedBy;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public boolean isAutoEscalated() {
        return autoEscalated;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Integer getCourierAmount() {
        return courierAmount;
    }

    public Integer getRequesterAmount() {
        return requesterAmount;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
