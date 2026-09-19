/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: entity scaffolded for issue #61 per team decision D5
 * (docs/notification-service.md): last applied per-order sequence,
 * stored in the same schema as notifications for atomic commits.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Last applied event sequence per order; events with a sequence at or
 * below it are stale and discarded (D5, F2.4).
 */
@Entity
@Table(name = "order_sequences")
public class OrderSequence {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "last_applied_sequence", nullable = false)
    private long lastAppliedSequence;

    protected OrderSequence() {
    }

    public OrderSequence(String orderId, long lastAppliedSequence) {
        this.orderId = orderId;
        this.lastAppliedSequence = lastAppliedSequence;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getLastAppliedSequence() {
        return lastAppliedSequence;
    }

    public void setLastAppliedSequence(long lastAppliedSequence) {
        this.lastAppliedSequence = lastAppliedSequence;
    }
}
