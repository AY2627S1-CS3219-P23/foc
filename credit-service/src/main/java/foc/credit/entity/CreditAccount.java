/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the credit_account table, transcribed from
 * V1__baseline.sql (docs/credit-service.md "Schema"); no logic.
 * 2026-10-28, Claude Code (Opus 5.5), issue #267: closedAt, V6's
 * closed_at (team decision USR-4).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A user's credit balances, one row per user (D6). Total is
 * {@code available + reserved}, never stored.
 */
@Entity
@Table(name = "credit_account")
public class CreditAccount {

    // the user-service's ID, an opaque string here
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // when the user was purged; null = open (USR-4)
    @Column(name = "closed_at")
    private Instant closedAt;

    protected CreditAccount() {
    }

    public CreditAccount(String userId, int available, int reserved, Instant createdAt) {
        this.userId = userId;
        this.available = available;
        this.reserved = reserved;
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isClosed() {
        return closedAt != null;
    }
}
