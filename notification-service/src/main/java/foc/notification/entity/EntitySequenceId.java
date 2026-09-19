/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: composite key for issue #64 per the author's decision that
 * sequence tracking is scoped by the envelope's (entityType,
 * entityId) pair (D5 generalized; docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link EntitySequence}: the envelope's
 * {@code (entityType, entityId)} pair, the scope within which
 * {@code sequence} values are comparable (D5, F2.4).
 */
@Embeddable
public class EntitySequenceId implements Serializable {

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    protected EntitySequenceId() {
    }

    public EntitySequenceId(String entityType, String entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EntitySequenceId that)) {
            return false;
        }
        return Objects.equals(entityType, that.entityType)
                && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId);
    }
}
