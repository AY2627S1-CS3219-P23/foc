/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: empty Spring Data repository for the scaffold; queries come
 * with the features that need them.
 * 2026-10-12, Claude Code (Opus 5.5), issue #170: the publisher's poll
 * query.
 * 2026-10-13, Claude Code (Opus 5.5), PR #188 review (Leong Wei Zhi):
 * the poll reads only the replies that are due (team decision), and an
 * insert that never overwrites, as ReservedRepository has.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.credit.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // the publisher's poll (D10): the unsent replies that are due, oldest
    // first, on the partial index idx_outbox_event_unsent. A reply the
    // broker returned is not due until its next_attempt_at, so it does
    // not hold a place in the batch in the meantime.
    @Query("""
        select e from OutboxEvent e
        where e.sentAt is null and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
        order by e.createdAt
        """)
    List<OutboxEvent> findDue(@Param("now") Instant now, Limit limit);

    /**
     * Inserts a new reply, never updates one: a second insert for the
     * same event ID fails on the primary key. Use this for a new reply,
     * not {@code save}, which merges: it reads the row first, and for an
     * ID already stored would set a published reply back to unsent.
     */
    default void insert(OutboxEvent reply) {
        insertRow(reply.getEventId(), reply.getEventType(), reply.getPayload(), reply.getCreatedAt());
    }

    @Modifying
    @Query(value = """
        insert into outbox_event (event_id, event_type, payload, created_at)
        values (:eventId, :eventType, :payload, :createdAt)
        """, nativeQuery = true)
    void insertRow(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
            @Param("payload") String payload, @Param("createdAt") Instant createdAt);
}
