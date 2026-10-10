/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the credit_lot table, transcribed from
 * V1__baseline.sql (docs/credit-service.md "Schema"); no logic.
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
 * A user's available credits that share one expiry date (D4, F6.1). The
 * row is deleted when spent to zero.
 */
@Entity
@Table(name = "credit_lot")
public class CreditLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private String ownerId;

    @Column(nullable = false)
    private int remaining;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    // null never expires (the sign-up credits)
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    protected CreditLot() {
    }

    public CreditLot(String ownerId, int remaining, Instant earnedAt, Instant expiresAt) {
        this.ownerId = ownerId;
        this.remaining = remaining;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
