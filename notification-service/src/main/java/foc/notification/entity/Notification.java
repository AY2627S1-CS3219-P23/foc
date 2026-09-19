/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: entity scaffolded for issue #61; columns derive from the
 * team-decided event envelope and requirements in
 * docs/notification-service.md (F1.2, F3.1, F3.2).
 * 2026-09-19, issue #64: columns renamed to the envelope's
 * entity vocabulary (order_id/type -> entity_type/entity_id/
 * event_type) and moved to the entity package — both author
 * decisions.
 * 2026-09-19, Method-B refactor (D16-D19): entity_type/entity_id
 * columns removed with the stale-discard mechanism (author decision
 * D19); the payload now carries the event's business fields (incl.
 * orderId).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One notification row per associated party of an event (F1.2),
 * listed and marked read/unread via the REST API (F3.1, F3.2).
 * {@code event_type} stores the event's canonical identity string
 * (e.g. {@code order.accepted}, D18); the payload is the event's
 * business fields serialized to JSON (which is where the order
 * reference lives, e.g. {@code orderId}).
 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_recipient", columnList = "recipient_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(String recipientId, String eventId, String eventType,
            String payload, Instant occurredAt) {
        this.recipientId = recipientId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
