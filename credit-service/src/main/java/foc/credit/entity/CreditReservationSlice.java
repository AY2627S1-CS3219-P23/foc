/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the credit_reservation_slice table, transcribed
 * from V1__baseline.sql (docs/credit-service.md "Schema"). The doc names
 * no class for this table; the name follows the table's. No logic.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Part of a reservation taken from one lot, so a release returns the
 * credits with their original expiry (D4).
 */
@Entity
@Table(name = "credit_reservation_slice")
public class CreditReservationSlice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_ref", nullable = false, updatable = false)
    private String requestRef;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    // null never expires
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    protected CreditReservationSlice() {
    }

    public CreditReservationSlice(String requestRef, int amount, Instant earnedAt, Instant expiresAt) {
        this.requestRef = requestRef;
        this.amount = amount;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public int getAmount() {
        return amount;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
