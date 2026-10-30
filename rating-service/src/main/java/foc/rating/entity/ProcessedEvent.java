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
 * 2026-10-30, Claude Code (Fable 5), PR #271 review (Sinnez1):
 * Persistable via the PostPersist/PostLoad callback pattern, so save()
 * persists instead of merging — a merge SELECT would adopt a
 * concurrently committed duplicate and the PK backstop would never
 * fire; persist also drops that SELECT. (A constant isNew()=true was
 * tried first and reverted: SimpleJpaRepository.delete skips entities
 * it believes are new, which broke the tests' deleteAll.)
 * Author review: [pending]
 */
package foc.rating.entity;

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
 * that rejects a concurrent duplicate delivery. {@code processed_at}
 * is indexed for a future retention purge (notification-service
 * precedent, its issue #137) — rating-service has no purge yet.
 *
 * <p>{@link #isNew()} is {@code true} until the entity has been
 * persisted or loaded (the Spring Data callback pattern), so
 * {@code save} on a fresh instance issues a {@code persist} — an
 * INSERT that trips the primary key on a concurrent duplicate — rather
 * than a {@code merge}, whose lookup would silently adopt the row the
 * other transaction committed, and loaded rows still delete normally.
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
