/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the outbox publisher's poll with the repository and the broker
 * mocked: what is marked sent, and what each kind of failure does to
 * the rest of the poll (the team's decision).
 * 2026-10-13, Claude Code (Opus 5.5), PR #188 review (Leong Wei Zhi):
 * the poll asks for the replies that are due, and a returned reply is
 * given its next attempt time (team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.service.ReplyPublisher.Result;

class OutboxPublisherTest {

    private static final int BATCH_SIZE = 100;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);
    private final ReplyPublisher broker = mock(ReplyPublisher.class);
    private final OutboxPublisher publisher =
        new OutboxPublisher(outboxEvents, broker, BATCH_SIZE, RETRY_DELAY.toMillis());

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
    void aPollReadsAtMostOneBatchOfTheRepliesDueNow() {
        unsent();
        Instant before = Instant.now();

        publisher.publishUnsent();

        ArgumentCaptor<Instant> asOf = ArgumentCaptor.forClass(Instant.class);
        verify(outboxEvents).findDue(asOf.capture(), eq(Limit.of(BATCH_SIZE)));
        assertThat(asOf.getValue()).isBetween(before, Instant.now());
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
    }

    @Test
    void aReplyThatIsNotSentIsNotDueAgainUntilTheRetryDelayHasPassed() {
        unsent(first, second);
        when(broker.publish(first)).thenReturn(Result.NOT_SENT);
        when(broker.publish(second)).thenReturn(Result.SENT);
        Instant before = Instant.now();

        publisher.publishUnsent();

        assertThat(first.getNextAttemptAt())
            .isBetween(before.plus(RETRY_DELAY), Instant.now().plus(RETRY_DELAY));
        verify(outboxEvents).save(first);
        // a reply that was sent has no next attempt
        assertThat(second.getNextAttemptAt()).isNull();
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
        // the broker being in trouble says nothing about these replies:
        // they are due on the very next poll
        assertThat(second.getNextAttemptAt()).isNull();
        verify(outboxEvents, never()).save(second);
    }

    private void unsent(OutboxEvent... replies) {
        when(outboxEvents.findDue(any(Instant.class), eq(Limit.of(BATCH_SIZE)))).thenReturn(List.of(replies));
    }

    private static OutboxEvent reply() {
        return new OutboxEvent(UUID.randomUUID(), "credit.reserved", "{}", Instant.now());
    }
}
