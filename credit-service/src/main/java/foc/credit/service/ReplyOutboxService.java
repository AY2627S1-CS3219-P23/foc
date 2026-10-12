/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the ReplyOutbox implementation, replacing the scaffold stub:
 * one outbox_event row per reply, written in the caller's transaction
 * (docs/credit-service.md D10).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.core.DomainEvent;
import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores a reply as an {@code outbox_event} row for the publisher to
 * send (D10). The payload is the message body exactly as it goes on the
 * wire: the event's fields plus {@code eventType}, which the records do
 * not store. The row's key is the reply's own {@code eventId}.
 */
@Service
public class ReplyOutboxService implements ReplyOutbox {

    static final String EVENT_TYPE_FIELD = "eventType";

    private final OutboxEventRepository outboxEvents;
    private final JsonMapper jsonMapper;

    public ReplyOutboxService(OutboxEventRepository outboxEvents, JsonMapper jsonMapper) {
        this.outboxEvents = outboxEvents;
        this.jsonMapper = jsonMapper;
    }

    // MANDATORY: a call outside a transaction fails instead of committing
    // a reply with no balance change behind it
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DomainEvent reply) {
        ObjectNode body = (ObjectNode) jsonMapper.valueToTree(reply);
        body.put(EVENT_TYPE_FIELD, reply.eventType());
        outboxEvents.save(new OutboxEvent(
            UUID.fromString(reply.eventId()), reply.eventType(), jsonMapper.writeValueAsString(body), Instant.now()));
    }
}
