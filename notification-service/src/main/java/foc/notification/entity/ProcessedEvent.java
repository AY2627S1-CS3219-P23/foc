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
 * 2026-11-06, Claude Code (Opus 5.5), issue #48 (DP10): Persistable, so
 * save() INSERTs (persist) instead of merging — the insert-first dedupe
 * (team decision, Leong Wei Zhi via options Q&A), mirroring rating-service
 * D4: isNew() is true until the entity is persisted or loaded (the
 * PostPersist/PostLoad callback pattern), not a constant true, which would
 * make SimpleJpaRepository.delete skip loaded rows. Author review: Leong
 * Wei Zhi, pending pull request review.
 */
package foc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * Event IDs already applied; the primary key is the unique constraint
 * that rejects duplicate deliveries (D4, F2.1). Rows older than the
 * retention window are purged by the retention scheduler (issue #137),
 * which scans {@code processed_at} — hence the index.
 *
 * <p>{@link #isNew()} is {@code true} until the entity has been persisted
 * or loaded (the Spring Data callback pattern, as rating-service's copy),
 * so {@code save} on a fresh instance issues a {@code persist} — an INSERT
 * that trips the primary key on a concurrent duplicate from another
 * replica (DP10, issue #48) — rather than a {@code merge}, whose lookup
 * would silently adopt the row the other transaction committed. Loaded
 * rows still delete normally.
 */
@Entity
@Table(name = "processed_events",
        indexes = @Index(name = "idx_processed_events_processed_at", columnList = "processed_at"))
public class ProcessedEvent implements Persistable<String> {

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

    @Transient
    private boolean isNew = true;

    @Override
    @Transient
    public String getId() {
        return eventId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
