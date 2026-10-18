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
 * 2026-10-17, issue #66: the REST API's reads (paged list, unread
 * filter, unread count) and read-flag writes. The writes are
 * ownership-scoped bulk updates whose row count answers 404 — one
 * statement, no entity load (author decision via options Q&A,
 * docs/notification-service.md D26).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.repository;

import foc.notification.entity.Notification;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** One recipient's notifications (F3.1); idx_notifications_recipient serves the filter. */
	Page<Notification> findByRecipientId(String recipientId, Pageable pageable);

	/** The unread subset of the same list (?unread=true). */
	Page<Notification> findByRecipientIdAndReadFalse(String recipientId, Pageable pageable);

	/** What the bell badge shows; the client re-syncs it after reads and reconnects. */
	long countByRecipientIdAndReadFalse(String recipientId);

	/**
	 * Sets one notification's read flag (F3.2), but only if the row is the
	 * caller's: the recipient condition makes "someone else's" and "does
	 * not exist" the same count of 0, which the service answers 404 — no
	 * existence oracle. Counts matched rows, so repeating a mark is the
	 * same success, not an error. {@code @Transactional} for the same
	 * reason as the purge below; {@code clearAutomatically} keeps any
	 * managed copy from shadowing the bulk write.
	 */
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("update Notification n set n.read = :read where n.id = :id and n.recipientId = :recipientId")
	int updateReadFlag(@Param("id") Long id, @Param("recipientId") String recipientId, @Param("read") boolean read);

	/** Marks every unread row of one recipient read, in one statement. */
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.read = false")
	int markAllRead(@Param("recipientId") String recipientId);

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
