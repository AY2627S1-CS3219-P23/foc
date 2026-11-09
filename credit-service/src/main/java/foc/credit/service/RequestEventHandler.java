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
 * 2026-10-11, PR #175 review (Leong Wei Zhi), team decision: a
 * completion with no record throws, so it is retried; the settle
 * result is chosen by a switch with no default.
 * 2026-10-18, issue #165 (reviewer's suggestion): a redelivered
 * submission already decided enqueues its reply again.
 * PR #212 review (Leong Wei Zhi), reviewer's suggestion: that reply is
 * described as rebuilt from the event, with the record's decision.
 * 2026-10-21, issue #234: the rejection carries reasonCode beside the
 * reason text (team decision, DSP-12).
 * 2026-10-22, PR #243 review follow-up: the reason text constants
 * renamed with a _TEXT suffix, so they no longer share a name with the
 * reasonCode constants passed beside them.
 * 2026-10-25, issue #235: request.dispute-resolved settles through
 * resolve, with the outcomes of a completion (team decision, DSP-13).
 * Author review: Ryan Ang, pending pull request review.
 
 * 2026-11-04, Claude Code (Fable 5.1), notification D34 (author
 * decision via options Q&A): the parties argument/assertions removed —
 * events carry no recipient list; RequestExpired gains a nullable
 * courierId. Author review: Leong Wei Zhi, pending pull request review.
 * 2026-11-08, Claude Code (Opus 5.5), Credit NFR1.1 (PR #313): a settled
 * transfer, release or dispute split also enqueues credit.transferred,
 * credit.released or credit.dispute-settled, for the credit-change
 * notification (team decision). Author review: Ryan Ang, pending pull
 * request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.credit.CreditDisputeSettled;
import foc.contracts.events.credit.CreditReleased;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.credit.CreditTransferred;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestDisputeResolved;
import foc.contracts.events.request.RequestEvent;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestSubmitted;
import foc.credit.entity.Reserved;
import foc.credit.repository.ReservedRepository;

/**
 * Applies one request event in one transaction (docs/credit-service.md,
 * "How each event is handled"): the credit operation and, for a
 * reservation, its reply in the outbox commit together or not at all
 * (D10). A settled transfer, release or dispute split commits its
 * credit event the same way, for the notification of the change. The listener only converts the message, calls this and settles
 * the delivery on the outcome, so no broker type is imported here.
 */
@Service
public class RequestEventHandler {

    /** What the listener does with the delivery once the event is applied. */
    public enum Outcome {
        // applied, or nothing to do: acknowledge
        HANDLED,
        // conflicts with the request's record, or is missing a field:
        // dead-letter, no retry (D11)
        INVALID_STATE
    }

    private static final Logger log = LoggerFactory.getLogger(RequestEventHandler.class);

    static final String PRODUCER = "credit-service";
    // the rejection's reason text, for people; its reasonCode is the
    // matching CreditReservationRejected constant
    static final String INSUFFICIENT_CREDITS_TEXT = "insufficient available credits";
    static final String REWARD_BELOW_ONE_TEXT = "reward must be at least 1 credit";

    private final CreditOperations operations;
    private final ReplyOutbox outbox;
    private final ReservedRepository reservations;

    public RequestEventHandler(CreditOperations operations, ReplyOutbox outbox,
            ReservedRepository reservations) {
        this.operations = operations;
        this.outbox = outbox;
        this.reservations = reservations;
    }

    // an exception rolls the transaction back and reaches the listener,
    // which sends the event to the retry queue
    @Transactional
    public Outcome handle(RequestEvent event) {
        return switch (event) {
            case RequestSubmitted e -> onSubmitted(e);
            case RequestCompleted e -> onCompleted(e);
            case RequestCancelled e -> onReleased(e, operations.release(e.requestId(), e.requesterId()));
            case RequestExpired e -> onReleased(e, operations.release(e.requestId(), e.requesterId()));
            case RequestDisputeResolved e -> onDisputeResolved(e);
            // A request event the Credit Service has no part in. Only the
            // five events above are bound to the queue, so this is one
            // published to it by hand: acknowledged, since there is nothing
            // to inspect, but logged so it can be traced.
            default -> {
                log.warn("No handler for request event {} (eventId {}); acknowledged without action",
                    event.getClass().getSimpleName(), event.eventId());
                yield Outcome.HANDLED;
            }
        };
    }

    // The reply is rebuilt from the event, an ALREADY_* one too: only the
    // decision comes from the record, not its fields.
    private Outcome onSubmitted(RequestSubmitted e) {
        // no default: a new ReserveResult does not compile until it is given a reply
        DomainEvent reply = switch (operations.reserve(e.requestId(), e.requesterId(), e.reward())) {
            case RESERVED, ALREADY_RESERVED -> new CreditReserved(
                newEventId(), Instant.now(), PRODUCER, e.correlationId(), e.requestId(), e.requesterId(), e.reward());
            case REJECTED_INSUFFICIENT_CREDITS, ALREADY_REJECTED_INSUFFICIENT_CREDITS ->
                rejected(e, INSUFFICIENT_CREDITS_TEXT, CreditReservationRejected.INSUFFICIENT_CREDITS);
            case REJECTED_INVALID_AMOUNT, ALREADY_REJECTED_INVALID_AMOUNT ->
                rejected(e, REWARD_BELOW_ONE_TEXT, CreditReservationRejected.INVALID_AMOUNT);
            // settled: the request has moved past its reservation
            case DUPLICATE -> null;
        };
        if (reply != null) {
            outbox.enqueue(reply);
        }
        return Outcome.HANDLED;
    }

    // The parties and amount come from the request's record, which the
    // operation has just settled under its lock, not from the event.
    private Outcome onCompleted(RequestCompleted e) {
        SettleResult result = operations.transfer(e.requestId(), e.courierId());
        if (result == SettleResult.SETTLED) {
            Reserved record = settledRecord(e.requestId());
            outbox.enqueue(new CreditTransferred(newEventId(), Instant.now(), PRODUCER, e.correlationId(),
                e.requestId(), record.getRequesterId(), record.getCourierId(), record.getAmount()));
        }
        return settle(e.requestId(), result);
    }

    private Outcome onReleased(RequestEvent e, SettleResult result) {
        if (result == SettleResult.SETTLED) {
            Reserved record = settledRecord(e.requestId());
            // a cancel or expiry that arrived before its reservation
            // settles an amount-0 record: no balance moved, so no event
            if (record.getAmount() > 0) {
                outbox.enqueue(new CreditReleased(newEventId(), Instant.now(), PRODUCER, e.correlationId(),
                    e.requestId(), record.getRequesterId(), record.getAmount()));
            }
        }
        return settle(e.requestId(), result);
    }

    private Outcome onDisputeResolved(RequestDisputeResolved e) {
        SettleResult result = operations.resolve(e.requestId(), e.courierId(), e.courierAmount(), e.requesterAmount());
        if (result == SettleResult.SETTLED) {
            Reserved record = settledRecord(e.requestId());
            // both parties, a share of 0 included (team decision)
            outbox.enqueue(new CreditDisputeSettled(newEventId(), Instant.now(), PRODUCER, e.correlationId(),
                e.requestId(), record.getRequesterId(), record.getCourierId(), record.getCourierShare(),
                record.getAmount() - record.getCourierShare()));
        }
        return settle(e.requestId(), result);
    }

    // SETTLED means the record exists: the operation locked or inserted it
    private Reserved settledRecord(String requestRef) {
        return reservations.findById(requestRef).orElseThrow();
    }

    private static CreditReservationRejected rejected(RequestSubmitted e, String reason, String reasonCode) {
        return new CreditReservationRejected(
            newEventId(), Instant.now(), PRODUCER, e.correlationId(), e.requestId(), e.requesterId(), e.reward(), reason, reasonCode);
    }

    // no default: a new SettleResult does not compile until it is given an outcome
    private static Outcome settle(String requestRef, SettleResult result) {
        return switch (result) {
            // both acknowledged; they differ for logs and tests
            case SETTLED, DUPLICATE -> Outcome.HANDLED;
            case INVALID_STATE -> Outcome.INVALID_STATE;
            // the completion or resolution overtook its reservation: retried
            // like any failure, and dead-lettered once the attempts run out (D11)
            case NO_RESERVATION -> throw new ReservationNotFoundException(requestRef);
        };
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
