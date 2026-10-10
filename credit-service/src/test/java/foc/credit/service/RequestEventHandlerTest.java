/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: unit tests for RequestEventHandler against a mocked
 * CreditOperations and ReplyOutbox (the author's instruction): which
 * operation each event calls, which results enqueue a reply and with
 * what fields, and the outcome handed back to the listener.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestEvent;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestSubmitted;
import foc.credit.service.RequestEventHandler.Outcome;

@ExtendWith(MockitoExtension.class)
class RequestEventHandlerTest {

    private static final Instant SENT_AT = Instant.parse("2026-10-10T08:00:00Z");

    @Mock
    private CreditOperations operations;

    @Mock
    private ReplyOutbox outbox;

    @InjectMocks
    private RequestEventHandler handler;

    private static RequestSubmitted submitted(int reward) {
        return new RequestSubmitted("evt-1", SENT_AT, "order-service", "corr-1", List.of("u-req"),
            "r-1", "u-req", "UTown", "PGP", null, reward);
    }

    private DomainEvent enqueuedReply() {
        ArgumentCaptor<DomainEvent> reply = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).enqueue(reply.capture());
        return reply.getValue();
    }

    @Test
    void reservedSubmissionEnqueuesCreditReserved() {
        when(operations.reserve("r-1", "u-req", 3)).thenReturn(ReserveResult.RESERVED);

        assertThat(handler.handle(submitted(3))).isEqualTo(Outcome.HANDLED);

        assertThat(enqueuedReply()).isInstanceOfSatisfying(CreditReserved.class, reply -> {
            assertThat(reply.requestId()).isEqualTo("r-1");
            assertThat(reply.requesterId()).isEqualTo("u-req");
            assertThat(reply.amount()).isEqualTo(3);
            assertThat(reply.producer()).isEqualTo("credit-service");
            assertThat(reply.correlationId()).isEqualTo("corr-1");
            assertThat(reply.parties()).isEmpty();
            // its own identity and time, not the request event's
            assertThat(reply.eventId()).isNotBlank().isNotEqualTo("evt-1");
            assertThat(reply.occurredAt()).isAfter(SENT_AT);
        });
    }

    @Test
    void refusedSubmissionEnqueuesTheRejectionWithItsReason() {
        when(operations.reserve("r-1", "u-req", 9)).thenReturn(ReserveResult.REJECTED);

        assertThat(handler.handle(submitted(9))).isEqualTo(Outcome.HANDLED);

        assertThat(enqueuedReply()).isInstanceOfSatisfying(CreditReservationRejected.class, reply -> {
            assertThat(reply.requestId()).isEqualTo("r-1");
            assertThat(reply.requesterId()).isEqualTo("u-req");
            assertThat(reply.amount()).isEqualTo(9);
            assertThat(reply.correlationId()).isEqualTo("corr-1");
            assertThat(reply.parties()).isEmpty();
            assertThat(reply.reason()).isEqualTo(RequestEventHandler.INSUFFICIENT_CREDITS);
        });
    }

    @Test
    void rewardBelowOneIsRefusedWithItsOwnReason() {
        when(operations.reserve("r-1", "u-req", 0)).thenReturn(ReserveResult.REJECTED);

        handler.handle(submitted(0));

        assertThat(enqueuedReply()).isInstanceOfSatisfying(CreditReservationRejected.class,
            reply -> assertThat(reply.reason()).isEqualTo(RequestEventHandler.REWARD_BELOW_ONE));
    }

    @Test
    void duplicateSubmissionEnqueuesNothing() {
        when(operations.reserve("r-1", "u-req", 3)).thenReturn(ReserveResult.DUPLICATE);

        assertThat(handler.handle(submitted(3))).isEqualTo(Outcome.HANDLED);

        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @CsvSource({ "SETTLED, HANDLED", "DUPLICATE, HANDLED", "INVALID_STATE, INVALID_STATE" })
    void completionTransfersToTheCourier(SettleResult result, Outcome expected) {
        when(operations.transfer("r-1", "u-cou")).thenReturn(result);

        RequestCompleted completed = new RequestCompleted("evt-2", SENT_AT, "order-service", "corr-1",
            List.of("u-req", "u-cou"), "r-1", "u-req", "u-cou", "UTown", "PGP", null);

        assertThat(handler.handle(completed)).isEqualTo(expected);
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @CsvSource({ "SETTLED, HANDLED", "DUPLICATE, HANDLED", "INVALID_STATE, INVALID_STATE" })
    void cancellationReleasesToTheRequester(SettleResult result, Outcome expected) {
        when(operations.release("r-1", "u-req")).thenReturn(result);

        RequestCancelled cancelled = new RequestCancelled("evt-3", SENT_AT, "order-service", "corr-1",
            List.of("u-req"), "r-1", "u-req", null, "UTown", "PGP", null);

        assertThat(handler.handle(cancelled)).isEqualTo(expected);
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @CsvSource({ "SETTLED, HANDLED", "DUPLICATE, HANDLED", "INVALID_STATE, INVALID_STATE" })
    void expiryReleasesToTheRequester(SettleResult result, Outcome expected) {
        when(operations.release("r-1", "u-req")).thenReturn(result);

        RequestExpired expired = new RequestExpired("evt-4", SENT_AT, "order-service", "corr-1",
            List.of("u-req"), "r-1", "u-req", "UTown", "PGP", null);

        assertThat(handler.handle(expired)).isEqualTo(expected);
        verifyNoInteractions(outbox);
    }

    @Test
    void otherRequestEventsAreAcknowledgedUntouched() {
        assertThat(handler.handle(mock(RequestEvent.class))).isEqualTo(Outcome.HANDLED);

        verifyNoInteractions(operations, outbox);
    }
}
