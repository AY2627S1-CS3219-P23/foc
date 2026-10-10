/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the credit_reservation table, transcribed from
 * V1__baseline.sql; the class name is the design doc's
 * (docs/credit-service.md "Schema"). No logic.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The reservation for one request, one row per request. The primary key
 * on {@code request_ref} is the duplicate check (D3).
 */
@Entity
@Table(name = "credit_reservation")
public class Reserved {

    // the order-service's request ID, an opaque string here
    @Id
    @Column(name = "request_ref", nullable = false, updatable = false)
    private String requestRef;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private String requesterId;

    // null until transfer
    @Column(name = "courier_id")
    private String courierId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // null while HELD
    @Column(name = "settled_at")
    private Instant settledAt;

    protected Reserved() {
    }

    public Reserved(String requestRef, String requesterId, int amount, ReservationStatus status,
            Instant createdAt) {
        this.requestRef = requestRef;
        this.requesterId = requesterId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getCourierId() {
        return courierId;
    }

    public void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    public int getAmount() {
        return amount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
