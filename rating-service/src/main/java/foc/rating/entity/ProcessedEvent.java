/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: a copy of notification-service's
 * foc.notification.entity.ProcessedEvent (Claude Code (Fable 5),
 * 2026-09-18/19, reviewed by Leong Wei Zhi), package changed. Copied
 * rather than shared, as the team does for everything but
 * foc-contracts. Here the schema is Flyway-owned (V2), so the entity
 * mirrors the migration instead of generating it.
 * Author review: [pending]
 */
package foc.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Event IDs already applied; the primary key is the unique constraint
 * that rejects a concurrent duplicate delivery. {@code processed_at}
 * is indexed for a future retention purge (notification-service
 * precedent, its issue #137) — rating-service has no purge yet.
 */
@Entity
@Table(name = "processed_events",
        indexes = @Index(name = "idx_processed_events_processed_at", columnList = "processed_at"))
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
