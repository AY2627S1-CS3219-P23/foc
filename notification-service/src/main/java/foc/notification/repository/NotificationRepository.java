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
 * 2026-10-30, Claude Code (Fable 5), issue #266: payload-pattern bulk
 * delete for the request.dropped ingest prune (author decision D33 via
 * options Q&A, docs/notification-service.md).
 * 2026-10-31, Claude Code (Fable 5), PR #277 review (@Sinnez1): the
 * prune delete gains an occurredAt <= bound, and a count query serves
 * the new ingest guard against courier events reordered past the drop.
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

	/**
	 * Hard-deletes one recipient's rows whose payload matches
	 * {@code pattern} (a {@code LIKE} pattern, {@code \} the escape
	 * character) and whose event occurred at or before {@code cutoff}.
	 * The ingest prune on {@code request.dropped} (D33): the request
	 * reference lives inside the JSON payload, not in a column, so the
	 * match is textual — portable across H2 and Postgres where a
	 * {@code jsonb} query would not be. The {@code occurredAt} bound
	 * keeps a retried drop (its first transaction rolled back, so the
	 * dedupe never saw it) from taking a re-accept's newer rows with it
	 * (PR #277 review); a tie counts as before the drop. No
	 * {@code @Transactional} here, unlike the purge above: the only
	 * caller is the ingest processor, whose transaction must also cover
	 * this delete so a redelivered event replays both or neither.
	 */
	@Modifying(clearAutomatically = true)
	@Query("delete from Notification n where n.recipientId = :recipientId"
			+ " and n.occurredAt <= :cutoff and n.payload like :pattern escape '\\'")
	int deleteByRecipientIdAndPayloadLike(@Param("recipientId") String recipientId,
			@Param("pattern") String pattern, @Param("cutoff") Instant cutoff);

	/**
	 * How many rows of {@code eventType} occurred at or after
	 * {@code since} with a payload matching both {@code LIKE} patterns
	 * ({@code \} the escape character). The ingest guard's question
	 * (D33, PR #277 review): "was this request already dropped by this
	 * courier, at or after this event?" — asked of the requester's
	 * stored {@code request.dropped} row, whose payload carries both
	 * {@code requestId} and {@code courierId}. Two patterns because the
	 * payload's key order is serializer-defined. Runs inside the ingest
	 * transaction, so no {@code @Transactional}.
	 */
	@Query("select count(n) from Notification n where n.eventType = :eventType"
			+ " and n.occurredAt >= :since"
			+ " and n.payload like :patternA escape '\\' and n.payload like :patternB escape '\\'")
	long countByEventTypeSincePayloadMatchingBoth(@Param("eventType") String eventType,
			@Param("since") Instant since,
			@Param("patternA") String patternA, @Param("patternB") String patternB);
}
