/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's foc.order.EventOutbox without its
 * request_id parameter (DSP-17, not needed here), with the package
 * changed; copied, not shared, by the author's decision.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.contracts.events.core.DomainEvent;
import foc.rating.entity.OutboxEvent;
import foc.rating.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a rating event as an {@code outbox_event} row for the
 * {@link OutboxPublisher} to send. The payload is the message body
 * exactly as it goes on the wire: the event's fields plus
 * {@code eventType}, which the records do not store. The row's key is
 * the event's own {@code eventId}, and the row is inserted, never saved:
 * a repeated event ID fails instead of overwriting the stored event.
 */
@Service
public class EventOutbox {

    static final String EVENT_TYPE_FIELD = "eventType";

    private final OutboxEventRepository outboxEvents;
    private final JsonMapper jsonMapper;

    public EventOutbox(OutboxEventRepository outboxEvents, JsonMapper jsonMapper) {
        this.outboxEvents = outboxEvents;
        this.jsonMapper = jsonMapper;
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
