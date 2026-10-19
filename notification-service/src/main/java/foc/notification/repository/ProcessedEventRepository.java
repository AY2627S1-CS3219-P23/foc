/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: repository scaffolded for issue #61.
 * 2026-09-19, issue #64: moved to the repository package (author
 * decision: controller-service-repository layout).
 * 2026-10-17, issue #137: deleteByProcessedAtBefore added so the
 * retention purge scheduler can sweep the dedupe table (author
 * decisions via neutral options: reuse the 30-day notification
 * retention window; index processed_at).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.ProcessedEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

	/**
	 * Hard-deletes every dedupe row recorded before {@code cutoff}
	 * (issue #137) in one bulk SQL {@code DELETE} — a derived
	 * {@code deleteBy...} method would instead select matching rows and
	 * remove them individually. {@code @Transactional} is required here:
	 * unlike the standard CRUD methods on {@link JpaRepository}, a
	 * custom {@code @Modifying} query is not transactional by default,
	 * and the scheduled caller provides no surrounding transaction of
	 * its own.
	 */
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("delete from ProcessedEvent p where p.processedAt < :cutoff")
	int deleteByProcessedAtBefore(@Param("cutoff") Instant cutoff);
}
