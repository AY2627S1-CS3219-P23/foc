/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: JPA entity for the credit_history table, transcribed from
 * V1__baseline.sql (docs/credit-service.md "Schema"); no logic.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One credit operation on one account, append-only (D5, F4.1.1): no
 * setters. {@code amount} is always positive; {@code type} says which
 * way it moved.
 */
@Entity
@Table(name = "credit_history")
public class CreditHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CreditHistoryType type;

    @Column(nullable = false, updatable = false)
    private int amount;

    // null if not for a request
    @Column(name = "request_ref", updatable = false)
    private String requestRef;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CreditHistoryEntry() {
    }

    public CreditHistoryEntry(String userId, CreditHistoryType type, int amount, String requestRef,
            Instant occurredAt) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.requestRef = requestRef;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public CreditHistoryType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
