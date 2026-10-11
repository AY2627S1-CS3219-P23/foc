/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, issue #169.
 * Scope: the request event handler over the real credit operations and
 * a real Postgres, with only the reply outbox mocked: the operation
 * joins the handler's transaction, so it commits with its reply or not
 * at all (docs/credit-service.md D10). The handler's own tests mock the
 * operations; added on the tool's review of this change.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestSubmitted;
import foc.credit.PostgresTestContainer;
import foc.credit.entity.CreditAccount;
import foc.credit.entity.ReservationStatus;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.ReservedRepository;
import foc.credit.service.RequestEventHandler.Outcome;

// Not @Transactional: the handler's own transaction commits or rolls
// back, which is what is under test. Every test uses its own IDs.
@SpringBootTest
class RequestEventHandlerCreditOperationsTest extends PostgresTestContainer {

    private static final Instant SENT_AT = Instant.parse("2026-10-11T08:00:00Z");

    private final String requester = "u-req-" + UUID.randomUUID();
    private final String courier = "u-cou-" + UUID.randomUUID();
    private final String request = "r-" + UUID.randomUUID();

    @MockitoBean
    private ReplyOutbox outbox;

    @Autowired
    private RequestEventHandler handler;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private ReservedRepository reservations;

    @Test
    void aReservationCommitsWithItsReply() {
        assertThat(handler.handle(submitted(3))).isEqualTo(Outcome.HANDLED);

        assertBalance(requester, 2, 3);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.HELD);
        ArgumentCaptor<DomainEvent> reply = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).enqueue(reply.capture());
        assertThat(reply.getValue()).isInstanceOfSatisfying(CreditReserved.class, reserved -> {
            assertThat(reserved.requestId()).isEqualTo(request);
            assertThat(reserved.amount()).isEqualTo(3);
        });
    }

    @Test
    void aReservationWhoseReplyCannotBeStoredLeavesNothingBehind() {
        IllegalStateException outboxFailure = new IllegalStateException("outbox unavailable");
        doThrow(outboxFailure).when(outbox).enqueue(any());

        assertThatThrownBy(() -> handler.handle(submitted(3))).isSameAs(outboxFailure);

        // the reservation, and the account it created, rolled back with the reply
        assertThat(reservations.findById(request)).isEmpty();
        assertThat(accounts.findById(requester)).isEmpty();
    }

    @Test
    void aCompletionPaysTheCourierOnceAndRefusesAnotherCourier() {
        handler.handle(submitted(3));

        assertThat(handler.handle(completed(courier))).isEqualTo(Outcome.HANDLED);
        assertThat(handler.handle(completed(courier))).as("redelivered").isEqualTo(Outcome.HANDLED);
        assertThat(handler.handle(completed("u-other-" + UUID.randomUUID())))
            .as("a different courier").isEqualTo(Outcome.INVALID_STATE);

        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.TRANSFERRED);
    }

    @Test
    void aCancellationReturnsTheCredits() {
        handler.handle(submitted(3));

        assertThat(handler.handle(new RequestCancelled("evt-cancelled", SENT_AT, "order-service", "corr-1",
            List.of(requester), request, requester, null, "UTown", "PGP", null))).isEqualTo(Outcome.HANDLED);

        assertBalance(requester, 5, 0);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void anExpiryThatArrivesFirstMakesTheLateSubmissionADuplicateWithNoReply() {
        assertThat(handler.handle(new RequestExpired("evt-expired", SENT_AT, "order-service", "corr-1",
            List.of(requester), request, requester, "UTown", "PGP", null))).isEqualTo(Outcome.HANDLED);

        assertThat(handler.handle(submitted(3))).isEqualTo(Outcome.HANDLED);

        assertBalance(requester, 5, 0);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.RELEASED);
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void aCompletionWithNothingHeldIsInvalid() {
        assertThat(handler.handle(completed(courier))).isEqualTo(Outcome.INVALID_STATE);

        assertThat(accounts.findById(courier)).isEmpty();
    }

    private RequestSubmitted submitted(int reward) {
        return new RequestSubmitted("evt-submitted", SENT_AT, "order-service", "corr-1", List.of(requester),
            request, requester, "UTown", "PGP", null, reward);
    }

    private RequestCompleted completed(String courierId) {
        return new RequestCompleted("evt-completed", SENT_AT, "order-service", "corr-1",
            List.of(requester, courierId), request, requester, courierId, "UTown", "PGP", null);
    }

    private void assertBalance(String userId, int available, int reserved) {
        CreditAccount account = accounts.findById(userId).orElseThrow();
        assertThat(List.of(account.getAvailable(), account.getReserved()))
            .as("available and reserved of " + userId)
            .containsExactly(available, reserved);
    }

    private ReservationStatus statusOf(String requestRef) {
        return reservations.findById(requestRef).orElseThrow().getStatus();
    }
}
