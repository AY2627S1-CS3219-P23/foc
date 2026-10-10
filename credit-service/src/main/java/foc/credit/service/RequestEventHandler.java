/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the request event handler, written from the author's plan: the
 * method shape, the transaction ownership, which results enqueue a reply,
 * the reply fields and the outcomes are the author's. The tool wrote the
 * code to that plan and chose the wording of the reward-below-1 reason.
 * Built against the CreditOperations and ReplyOutbox interfaces, which
 * are still stubs.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestEvent;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestSubmitted;

/**
 * Applies one request event in one transaction (docs/credit-service.md,
 * "How each event is handled"): the credit operation and, for a
 * reservation, its reply in the outbox commit together or not at all
 * (D10). The listener only converts the message, calls this and settles
 * the delivery on the outcome, so no broker type is imported here.
 */
@Service
public class RequestEventHandler {

    /** What the listener does with the delivery once the event is applied. */
    public enum Outcome {
        // applied, or nothing to do: acknowledge
        HANDLED,
        // conflicts with the request's record: dead-letter, no retry (D11)
        INVALID_STATE
    }

    static final String PRODUCER = "credit-service";
    static final String INSUFFICIENT_CREDITS = "insufficient available credits";
    static final String REWARD_BELOW_ONE = "reward must be at least 1 credit";

    private final CreditOperations operations;
    private final ReplyOutbox outbox;

    public RequestEventHandler(CreditOperations operations, ReplyOutbox outbox) {
        this.operations = operations;
        this.outbox = outbox;
    }

    // an exception rolls the transaction back and reaches the listener,
    // which sends the event to the retry queue
    @Transactional
    public Outcome handle(RequestEvent event) {
        return switch (event) {
            case RequestSubmitted e -> onSubmitted(e);
            case RequestCompleted e -> settle(operations.transfer(e.requestId(), e.courierId()));
            case RequestCancelled e -> settle(operations.release(e.requestId(), e.requesterId()));
            case RequestExpired e -> settle(operations.release(e.requestId(), e.requesterId()));
            // a request event the Credit Service has no part in
            default -> Outcome.HANDLED;
        };
    }

    private Outcome onSubmitted(RequestSubmitted e) {
        switch (operations.reserve(e.requestId(), e.requesterId(), e.reward())) {
            case RESERVED -> outbox.enqueue(new CreditReserved(
                newEventId(), Instant.now(), PRODUCER, e.correlationId(), List.of(),
                e.requestId(), e.requesterId(), e.reward()));
            case REJECTED -> outbox.enqueue(new CreditReservationRejected(
                newEventId(), Instant.now(), PRODUCER, e.correlationId(), List.of(),
                e.requestId(), e.requesterId(), e.reward(),
                e.reward() < 1 ? REWARD_BELOW_ONE : INSUFFICIENT_CREDITS));
            // the first delivery's reply is already in the outbox
            case DUPLICATE -> { }
        }
        return Outcome.HANDLED;
    }

    // SETTLED and DUPLICATE are both acknowledged; they differ for logs and tests
    private static Outcome settle(SettleResult result) {
        return result == SettleResult.INVALID_STATE ? Outcome.INVALID_STATE : Outcome.HANDLED;
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
