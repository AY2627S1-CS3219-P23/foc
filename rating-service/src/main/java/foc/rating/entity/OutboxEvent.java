/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's foc.order.model.OutboxEvent without
 * its request_id retry-settlement column (DSP-17, not needed here),
 * with the package changed; copied, not shared, by the author's
 * decision. Unlike order-service the table is Flyway-owned (V3).
 * Author review: [pending]
 */
package foc.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A rating event waiting to be published, written in the same
 * transaction as the rating row (transactional outbox, the
 * credit/order-service pattern).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // null = not yet published
    @Column(name = "sent_at")
    private Instant sentAt;

    // null = due at once; set when the broker returned the event
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String eventType, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }
}
