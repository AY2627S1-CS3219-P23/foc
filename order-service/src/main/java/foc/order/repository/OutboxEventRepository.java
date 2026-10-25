/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: a copy of credit-service's
 * foc.credit.repository.OutboxEventRepository (the publisher's poll and
 * the insert that never overwrites), with the package changed; copied,
 * not shared, by the author's decision.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: the insert writes
 * request_id (DSP-17); the newest settlement event for a request and the
 * reset that makes it due again, for retry settlement (DSP-10). Author
 * review: Ryan Ang, pending pull request review.
 */
package foc.order.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import foc.order.model.OutboxEvent;

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

    // retry settlement: the request's newest event of one of the types
    // (DSP-10's final events)
    Optional<OutboxEvent> findFirstByRequestIdAndEventTypeInOrderByCreatedAtDesc(
            Long requestId, Collection<String> eventTypes);

    // Makes an event unsent and due at once, keeping its event ID, so the
    // publisher sends the same message again and consumers deduplicate it
    // (DSP-10).
    @Modifying
    @Query("update OutboxEvent e set e.sentAt = null, e.nextAttemptAt = null where e.eventId = :eventId")
    int markUnsent(@Param("eventId") UUID eventId);

    /**
     * Inserts a new event, never updates one: a second insert for the
     * same event ID fails on the primary key. Use this for a new event,
     * not {@code save}, which merges: it reads the row first, and for an
     * ID already stored would set a published event back to unsent.
     */
    default void insert(OutboxEvent event) {
        insertRow(event.getEventId(), event.getEventType(), event.getPayload(), event.getCreatedAt(),
                event.getRequestId());
    }

    @Modifying
    @Query(value = """
        insert into outbox_event (event_id, event_type, payload, created_at, request_id)
        values (:eventId, :eventType, :payload, :createdAt, :requestId)
        """, nativeQuery = true)
    void insertRow(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
            @Param("payload") String payload, @Param("createdAt") Instant createdAt,
            @Param("requestId") Long requestId);
}
