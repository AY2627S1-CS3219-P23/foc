/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: repository for issue #64 (replaces OrderSequenceRepository
 * per the author's entity-vocabulary schema decision).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.EntitySequence;
import foc.notification.entity.EntitySequenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitySequenceRepository extends JpaRepository<EntitySequence, EntitySequenceId> {
}
