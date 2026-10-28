/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 (team decision USR-2): a copy of order-service's
       OutboxEventRepository (the publisher's poll and the insert that
       never overwrites) without request_id and the retry-settlement
       queries; copied, not shared.
       Same day, PR review (team decision): deleteSentBefore, a copy of
       credit-service's, for the retention purge.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import foc.user.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // the publisher's poll: the unsent events that are due, oldest first.
    // An event the broker returned is not due until its next_attempt_at,
    // so it does not hold a place in the batch in the meantime.
    @Query("""
        select e from OutboxEvent e
        where e.sentAt is null and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
        order by e.createdAt
        """)
    List<OutboxEvent> findDue(@Param("now") Instant now, Limit limit);

    /**
     * Inserts a new event, never updates one: a second insert for the
     * same event ID fails on the primary key. Use this for a new event,
     * not {@code save}, which merges: it reads the row first, and for an
     * ID already stored would set a published event back to unsent.
     */
    default void insert(OutboxEvent event) {
        insertRow(event.getEventId(), event.getEventType(), event.getPayload(), event.getCreatedAt());
    }

    /**
     * The retention purge: deletes the events sent before {@code cutoff}
     * in one bulk {@code DELETE}, on the partial index
     * idx_outbox_event_sent. An unsent event is never deleted, however
     * old. Transactional on its own, since the scheduled caller has no
     * transaction.
     */
    @Modifying
    @Transactional
    @Query("delete from OutboxEvent e where e.sentAt is not null and e.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = """
        insert into outbox_event (event_id, event_type, payload, created_at)
        values (:eventId, :eventType, :payload, :createdAt)
        """, nativeQuery = true)
    void insertRow(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
            @Param("payload") String payload, @Param("createdAt") Instant createdAt);
}
