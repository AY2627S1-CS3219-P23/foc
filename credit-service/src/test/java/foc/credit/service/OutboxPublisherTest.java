/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the outbox publisher's poll with the repository and the broker
 * mocked: what is marked sent, and what each kind of failure does to
 * the rest of the poll (the team's decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.service.ReplyPublisher.Result;

class OutboxPublisherTest {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);
    private final ReplyPublisher broker = mock(ReplyPublisher.class);
    private final OutboxPublisher publisher = new OutboxPublisher(outboxEvents, broker, BATCH_SIZE);

    private final OutboxEvent first = reply();
    private final OutboxEvent second = reply();
    private final OutboxEvent third = reply();

    @Test
    void aConfirmedReplyIsMarkedSent() {
        unsent(first);
        when(broker.publish(first)).thenReturn(Result.SENT);
        Instant before = Instant.now();

        publisher.publishUnsent();

        assertThat(first.getSentAt()).isBetween(before, Instant.now());
        verify(outboxEvents).save(first);
    }

    @Test
    void aPollReadsAtMostOneBatch() {
        unsent();

        publisher.publishUnsent();

        verify(outboxEvents).findBySentAtIsNullOrderByCreatedAtAsc(Limit.of(BATCH_SIZE));
        verify(broker, never()).publish(any());
    }

    @Test
    void aReplyThatIsNotSentStaysUnsentAndTheRestGoOn() {
        unsent(first, second, third);
        when(broker.publish(first)).thenReturn(Result.SENT);
        when(broker.publish(second)).thenReturn(Result.NOT_SENT);
        when(broker.publish(third)).thenReturn(Result.SENT);

        publisher.publishUnsent();

        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNull();
        assertThat(third.getSentAt()).isNotNull();
        verify(outboxEvents, never()).save(second);
    }

    @Test
    void anUnavailableBrokerStopsThePoll() {
        unsent(first, second, third);
        when(broker.publish(first)).thenReturn(Result.SENT);
        when(broker.publish(second)).thenReturn(Result.BROKER_UNAVAILABLE);

        publisher.publishUnsent();

        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNull();
        assertThat(third.getSentAt()).isNull();
        verify(broker, never()).publish(third);
    }

    private void unsent(OutboxEvent... replies) {
        when(outboxEvents.findBySentAtIsNullOrderByCreatedAtAsc(Limit.of(BATCH_SIZE))).thenReturn(List.of(replies));
    }

    private static OutboxEvent reply() {
        return new OutboxEvent(UUID.randomUUID(), "credit.reserved", "{}", Instant.now());
    }
}
