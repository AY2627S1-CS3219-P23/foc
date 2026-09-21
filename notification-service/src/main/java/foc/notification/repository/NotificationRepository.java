/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: repository scaffolded for issue #61.
 * 2026-09-19, issue #64: moved to the repository package (author
 * decision: controller-service-repository layout).
 * 2026-09-20, issue #68: deleteByCreatedAtBefore added for the
 * retention purge scheduler (team decision D9, docs/notification-service.md).
 * 2026-09-20, PR #81 Copilot review: switched from a derived
 * {@code deleteBy...} method (which loads and removes matching rows
 * one at a time, and is not guaranteed transactional outside a
 * caller-provided transaction) to an explicit bulk
 * {@code @Modifying @Query} with its own {@code @Transactional}.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.Notification;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * Hard-deletes every row stored before {@code cutoff} (D9/F3.4) in
	 * one bulk SQL {@code DELETE} — a derived {@code deleteBy...} method
	 * would instead select matching rows and remove them individually.
	 * {@code @Transactional} is required here: unlike the standard CRUD
	 * methods on {@link JpaRepository}, a custom {@code @Modifying}
	 * query is not transactional by default, and the scheduled caller
	 * provides no surrounding transaction of its own.
	 */
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("delete from Notification n where n.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
