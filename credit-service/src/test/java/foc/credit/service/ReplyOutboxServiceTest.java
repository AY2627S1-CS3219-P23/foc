/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the reply outbox against a real Postgres: a reply is stored
 * with its transaction, not stored when it rolls back, and refused
 * outside one.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.credit.PostgresTestContainer;
import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Not @Transactional: whether the row survives its transaction is what
// is under test. Every test uses its own event ID.
@SpringBootTest
class ReplyOutboxServiceTest extends PostgresTestContainer {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-12T08:00:00Z");

    private final UUID eventId = UUID.randomUUID();

    @Autowired
    private ReplyOutbox outbox;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void aReplyIsStoredUnsentWithItsWireBody() {
        Instant before = Instant.now();

        transaction.executeWithoutResult(status -> outbox.enqueue(reserved()));

        OutboxEvent stored = outboxEvents.findById(eventId).orElseThrow();
        assertThat(stored.getEventType()).isEqualTo("credit.reserved");
        assertThat(stored.getSentAt()).isNull();
        assertThat(stored.getCreatedAt()).isBetween(before.minusMillis(1), Instant.now().plusMillis(1));
        JsonNode body = jsonMapper.readTree(stored.getPayload());
        // the event's own fields, and the eventType the record does not store
        assertThat(body.get("eventType").stringValue()).isEqualTo("credit.reserved");
        assertThat(body.get("eventId").stringValue()).isEqualTo(eventId.toString());
        assertThat(body.get("occurredAt").stringValue()).isEqualTo("2026-10-12T08:00:00Z");
        assertThat(body.get("producer").stringValue()).isEqualTo("credit-service");
        assertThat(body.get("correlationId").stringValue()).isEqualTo("corr-1");
        assertThat(body.get("parties").isEmpty()).isTrue();
        assertThat(body.get("requestId").stringValue()).isEqualTo("r-1");
        assertThat(body.get("requesterId").stringValue()).isEqualTo("u-req");
        assertThat(body.get("amount").intValue()).isEqualTo(3);
    }

    @Test
    void aRejectionKeepsItsReason() {
        transaction.executeWithoutResult(status -> outbox.enqueue(new CreditReservationRejected(
            eventId.toString(), OCCURRED_AT, "credit-service", "corr-1", List.of(),
            "r-1", "u-req", 9, "insufficient available credits")));

        OutboxEvent stored = outboxEvents.findById(eventId).orElseThrow();
        assertThat(stored.getEventType()).isEqualTo("credit.reservation-rejected");
        JsonNode body = jsonMapper.readTree(stored.getPayload());
        assertThat(body.get("eventType").stringValue()).isEqualTo("credit.reservation-rejected");
        assertThat(body.get("reason").stringValue()).isEqualTo("insufficient available credits");
    }

    @Test
    void aReplyEnqueuedInARolledBackTransactionIsNotStored() {
        transaction.executeWithoutResult(status -> {
            outbox.enqueue(reserved());
            status.setRollbackOnly();
        });

        assertThat(outboxEvents.findById(eventId)).isEmpty();
    }

    @Test
    void enqueueOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outbox.enqueue(reserved()))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxEvents.findById(eventId)).isEmpty();
    }

    private CreditReserved reserved() {
        return new CreditReserved(eventId.toString(), OCCURRED_AT, "credit-service", "corr-1", List.of(),
            "r-1", "u-req", 3);
    }
}
