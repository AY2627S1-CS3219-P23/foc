/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the request event handler, written from the author's plan: the
 * method shape, the transaction ownership, which results enqueue a reply,
 * the reply fields and the outcomes are the author's. The tool wrote the
 * code to that plan and chose the wording of the reward-below-1 reason.
 * Built against the CreditOperations and ReplyOutbox interfaces, which
 * are still stubs.
 * PR #168 review (Leong Wei Zhi), team decisions among the reviewer's
 * options: the rejection reason comes from the ReserveResult instead of
 * being worked out from the reward, the reply is chosen by a switch
 * expression so every result must be handled, and a request event with
 * no handler is still acknowledged but logged.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.core.DomainEvent;
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

    private static final Logger log = LoggerFactory.getLogger(RequestEventHandler.class);

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
            // A request event the Credit Service has no part in. Only the
            // four events above are bound to the queue, so this is one
            // published to it by hand: acknowledged, since there is nothing
            // to inspect, but logged so it can be traced.
            default -> {
                log.warn("No handler for request event {} (eventId {}); acknowledged without action",
                    event.getClass().getSimpleName(), event.eventId());
                yield Outcome.HANDLED;
            }
        };
    }

    private Outcome onSubmitted(RequestSubmitted e) {
        // no default: a new ReserveResult does not compile until it is given a reply
        DomainEvent reply = switch (operations.reserve(e.requestId(), e.requesterId(), e.reward())) {
            case RESERVED -> new CreditReserved(
                newEventId(), Instant.now(), PRODUCER, e.correlationId(), List.of(),
                e.requestId(), e.requesterId(), e.reward());
            case REJECTED_INSUFFICIENT_CREDITS -> rejected(e, INSUFFICIENT_CREDITS);
            case REJECTED_INVALID_AMOUNT -> rejected(e, REWARD_BELOW_ONE);
            // a record exists: its reply, if one was owed, committed with it
            case DUPLICATE -> null;
        };
        if (reply != null) {
            outbox.enqueue(reply);
        }
        return Outcome.HANDLED;
    }

    private static CreditReservationRejected rejected(RequestSubmitted e, String reason) {
        return new CreditReservationRejected(
            newEventId(), Instant.now(), PRODUCER, e.correlationId(), List.of(),
            e.requestId(), e.requesterId(), e.reward(), reason);
    }

    // SETTLED and DUPLICATE are both acknowledged; they differ for logs and tests
    private static Outcome settle(SettleResult result) {
        return result == SettleResult.INVALID_STATE ? Outcome.INVALID_STATE : Outcome.HANDLED;
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
