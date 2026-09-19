/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: repository for issue #64 (replaces OrderSequenceRepository
 * per the author's entity-vocabulary schema decision). Locking read
 * added addressing Copilot review on PR #73.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.EntitySequence;
import foc.notification.entity.EntitySequenceId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EntitySequenceRepository extends JpaRepository<EntitySequence, EntitySequenceId> {

	/**
	 * Reads the sequence row with a pessimistic write lock
	 * ({@code SELECT ... FOR UPDATE}), so concurrent deliveries for
	 * the same entity serialize on the check-then-update and
	 * {@code last_applied_sequence} can never regress (D5, F2.4).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EntitySequence> findWithLockById(EntitySequenceId id);
}
