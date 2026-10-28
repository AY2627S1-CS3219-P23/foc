/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 (team decision USR-2): a copy of order-service's
       EventOutbox without the request id, plus the two user events it
       is used for (USR-1: envelope and userId only, no parties).
       Copied, not shared.
       Same day, PR review (team decision): correlationId is the user's
       id, so a user's events can be traced together across services.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.user.UserCreated;
import foc.contracts.events.user.UserPurged;
import foc.user.entity.OutboxEvent;
import foc.user.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores a user event as an {@code outbox_event} row for the
 * {@link OutboxPublisher} to send. The payload is the message body
 * exactly as it goes on the wire: the event's fields plus
 * {@code eventType}, which the records do not store. The row's key is
 * the event's own {@code eventId}, and the row is inserted, never saved:
 * a repeated event ID fails instead of overwriting the stored event.
 */
@Service
public class EventOutbox {

    static final String EVENT_TYPE_FIELD = "eventType";
    static final String PRODUCER = "user-service";

    private final OutboxEventRepository outboxEvents;
    private final JsonMapper jsonMapper;

    public EventOutbox(OutboxEventRepository outboxEvents, JsonMapper jsonMapper) {
        this.outboxEvents = outboxEvents;
        this.jsonMapper = jsonMapper;
    }

    // The correlationId is the user's id, as a request event's is the
    // request's: every event about one user can be found together.

    /** user.created, for an account whose row was just inserted (USR-8). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void userCreated(long userId) {
        String user = Long.toString(userId);
        enqueue(new UserCreated(UUID.randomUUID().toString(), Instant.now(), PRODUCER, user, List.of(), user));
    }

    /** user.purged, for an account the day-31 purge is deleting. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void userPurged(long userId) {
        String user = Long.toString(userId);
        enqueue(new UserPurged(UUID.randomUUID().toString(), Instant.now(), PRODUCER, user, List.of(), user));
    }

    // MANDATORY: a call outside a transaction fails instead of committing
    // an event with no state change behind it
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DomainEvent event) {
        ObjectNode body = (ObjectNode) jsonMapper.valueToTree(event);
        body.put(EVENT_TYPE_FIELD, event.eventType());
        outboxEvents.insert(new OutboxEvent(UUID.fromString(event.eventId()), event.eventType(),
                jsonMapper.writeValueAsString(body), Instant.now()));
    }
}
