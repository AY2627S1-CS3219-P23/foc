/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: entity scaffolded for issue #61 per team decision D4
 * (docs/notification-service.md): unique event IDs recorded for
 * duplicate detection, in the same schema as notifications so both
 * writes commit atomically.
 * 2026-09-19, issue #64: moved to the entity package (author decision:
 * controller-service-repository layout).
 * 2026-10-17, issue #137: processed_at index added for the retention
 * purge sweep (author decision via neutral options).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Event IDs already applied; the primary key is the unique constraint
 * that rejects duplicate deliveries (D4, F2.1). Rows older than the
 * retention window are purged by the retention scheduler (issue #137),
 * which scans {@code processed_at} — hence the index.
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
