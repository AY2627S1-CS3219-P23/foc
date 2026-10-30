/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's foc.order.repository
 * .OutboxEventRepository (the publisher's poll and the insert that
 * never overwrites) without its retry-settlement queries (DSP-10/17,
 * not needed here), with the package changed; copied, not shared, by
 * the author's decision.
 * Author review: [pending]
 */
package foc.rating.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.rating.entity.OutboxEvent;

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

    @Modifying
    @Query(value = """
        insert into outbox_event (event_id, event_type, payload, created_at)
        values (:eventId, :eventType, :payload, :createdAt)
        """, nativeQuery = true)
    void insertRow(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
            @Param("payload") String payload, @Param("createdAt") Instant createdAt);
}
