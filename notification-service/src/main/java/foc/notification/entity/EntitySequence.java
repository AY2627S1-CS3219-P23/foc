/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: replaces the issue-#61 OrderSequence entity for issue #64
 * per the author's decisions: entity-vocabulary schema and a
 * composite (entityType, entityId) sequence key (D5 generalized).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Last applied event sequence per (entityType, entityId); events with
 * a sequence at or below it are stale and discarded (D5, F2.4), so a
 * late-arriving earlier event can never move status backwards.
 */
@Entity
@Table(name = "entity_sequences")
public class EntitySequence {

    @EmbeddedId
    private EntitySequenceId id;

    @Column(name = "last_applied_sequence", nullable = false)
    private long lastAppliedSequence;

    protected EntitySequence() {
    }

    public EntitySequence(EntitySequenceId id, long lastAppliedSequence) {
        this.id = id;
        this.lastAppliedSequence = lastAppliedSequence;
    }

    public EntitySequenceId getId() {
        return id;
    }

    public long getLastAppliedSequence() {
        return lastAppliedSequence;
    }

    public void setLastAppliedSequence(long lastAppliedSequence) {
        this.lastAppliedSequence = lastAppliedSequence;
    }
}
