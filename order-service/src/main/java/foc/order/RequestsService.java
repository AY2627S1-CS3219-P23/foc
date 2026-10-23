/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12.
 * Scope: body of the author's RequestsService stub — one method per
 * OrderEvents constant that loads the request, asks OrderStateMachine
 * for the next state, sets the status and the entity's existing
 * courier/timestamp columns, and persists through RequestsRepository.
 * No validation, ownership checks or event publishing.
 * 2026-10-15: the saga with the Credit Service (docs/credit-service.md
 * D7-D8): create starts the request PENDING and writes request.submitted
 * to the outbox; reserve, reject and timeout leave PENDING and write
 * request.created, request.rejected and request.cancelled. Only these
 * four transitions publish, by the author's decision; the others still
 * publish nothing.
 * 2026-10-16: arrive (COLLECTED -> ARRIVED) sets arrivedAt and writes
 * request.courier-arrived to the outbox, by the author's decision.
 * Same day: status is the OrderStates enum, so no name conversion.
 * 2026-10-17: complete writes request.completed to the outbox, so the
 * Credit Service pays the courier (the author's decision).
 * Same day: cancel and expire write request.cancelled and
 * request.expired, so the Credit Service releases the reward to the
 * requester (the author's decision). DROP still publishes nothing
 * (until request.dropped, below).
 * 2026-10-18: accept refuses a courier who is the request's own
 * requester, answered 409 (the author's decision).
 * Same day: ownership checks (the author's rules): collect, arrive and
 * drop only by the assigned courier, cancel and complete only by the
 * requester; anyone else is told the request does not exist (404).
 * Same day: create checks its fields first (the author's rules): every
 * NOT NULL column present, text not blank, creditAmount at least 1
 * (F1.1.3) and deadline in the future (F1.1.2), all problems reported
 * at once as an InvalidRequestException (400). Then supplierName
 * required as well (the author's decision; the column stays nullable).
 * 2026-10-18: accept and collect write request.accepted and
 * request.collected (the author's decision); completeAutomatically for
 * the auto-complete sweep.
 * Same day: accept refuses a request whose deadline has passed (409), as
 * the expiry sweep may not have run yet (the author's decision).
 * Same day, Claude Code (Fable 5): drop writes request.dropped so the
 * requester is told (new event type, not request.cancelled — the
 * reward stays held, F5.2; the author's decision via options Q&A);
 * pending pull request review.
 * PR #214 review (@Sinnez1): the dropping courier passed to the event
 * factory explicitly, so the enqueue no longer has to precede the
 * field clear.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: each abnormal ending
 * records its close_reason (DSP-1: cancel, timeout, reject with the
 * reason the reply handler mapped, expire); every outbox row gets its
 * request's id (DSP-17); retrySettlement marks the request's newest final
 * event unsent again (DSP-10). Author review: Ryan Ang, pending pull
 * request review.
 */
package foc.order;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestDisputeResolved;
import foc.contracts.events.request.RequestExpired;
import foc.order.model.OutboxEvent;
import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import foc.order.repository.RequestsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

@Service
public class RequestsService {

    // The events that settle a request's held reward with the Credit
    // Service (DSP-10); request.dispute-resolved joins them with #239.
    static final List<String> SETTLEMENT_EVENT_TYPES = Stream.of(
                    RequestCompleted.class, RequestCancelled.class, RequestExpired.class, RequestDisputeResolved.class)
            .map(EventTypeRegistry::routingKeyFor).toList();

    private final RequestsRepository requestsRepository;
    private final EventOutbox outbox;
    private final OutboxEventRepository outboxEvents;

    public RequestsService(RequestsRepository requestsRepository, EventOutbox outbox,
            OutboxEventRepository outboxEvents) {
        this.requestsRepository = requestsRepository;
        this.outbox = outbox;
        this.outboxEvents = outboxEvents;
    }

    // CREATE: the caller fills in the request's own fields; status and
    // createdAt are set here. The request waits in PENDING until the
    // Credit Service answers request.submitted.
    @Transactional
    public Requests create(Requests request) {
        validateNew(request);
        request.setStatus(OrderStateMachine.INITIAL_STATE);
        request.setCreatedAt(Instant.now());
        Requests saved = requestsRepository.save(request);
        outbox.enqueue(RequestEvents.submitted(saved), saved.getId());
        return saved;
    }

    // RESERVE: the reward is held, so the request becomes visible to couriers.
    @Transactional
    public Requests reserve(Long requestId) {
        Requests request = transition(requestId, OrderEvents.RESERVE);
        outbox.enqueue(RequestEvents.created(request), request.getId());
        return request;
    }

    // REJECT: the reward could not be reserved; the request ends here.
    // reason is the Credit Service's text, passed on in request.rejected;
    // closeReason is what CreditReplyHandler mapped from the reply's
    // reasonCode, null when the reply had none.
    @Transactional
    public Requests reject(Long requestId, String reason, CloseReason closeReason) {
        Requests request = transition(requestId, OrderEvents.REJECT);
        request.setCloseReason(closeReason);
        outbox.enqueue(RequestEvents.rejected(request, reason), request.getId());
        return request;
    }

    // TIMEOUT: no reply in time. request.cancelled releases the reward
    // if the reservation happens after all.
    @Transactional
    public Requests timeout(Long requestId) {
        Requests request = transition(requestId, OrderEvents.TIMEOUT);
        request.setCloseReason(CloseReason.RESERVATION_TIMEOUT);
        outbox.enqueue(RequestEvents.cancelled(request), request.getId());
        return request;
    }

    // A requester cannot be the courier of their own request. Thrown as
    // IllegalStateException, so the controller answers 409 like any
    // refused transition; the rollback undoes the status change.
    @Transactional
    public Requests accept(Long requestId, Long courierId) {
        Requests request = transition(requestId, OrderEvents.ACCEPT);
        if (request.getRequesterId().equals(courierId)) {
            throw new IllegalStateException("A requester cannot accept their own request");
        }
        // past the deadline but not yet expired by the sweep: too late to take on
        if (!request.getDeadline().isAfter(Instant.now())) {
            throw new IllegalStateException("The request's deadline has passed");
        }
        request.setCourierId(courierId);
        request.setAcceptedAt(Instant.now());
        outbox.enqueue(RequestEvents.accepted(request), request.getId());
        return request;
    }

    @Transactional
    public Requests collect(Long requestId, Long callerId) {
        Requests request = transition(asCourier(requestId, callerId), OrderEvents.COLLECT);
        request.setCollectedAt(Instant.now());
        outbox.enqueue(RequestEvents.collected(request), request.getId());
        return request;
    }

    // ARRIVE: the courier is at the drop-off location; the requester is
    // told through request.courier-arrived.
    @Transactional
    public Requests arrive(Long requestId, Long callerId) {
        Requests request = transition(asCourier(requestId, callerId), OrderEvents.ARRIVE);
        request.setArrivedAt(Instant.now());
        outbox.enqueue(RequestEvents.courierArrived(request), request.getId());
        return request;
    }

    // CANCEL: the requester withdraws before any courier accepted;
    // request.cancelled releases the held reward to them.
    @Transactional
    public Requests cancel(Long requestId, Long callerId) {
        Requests request = transition(asRequester(requestId, callerId), OrderEvents.CANCEL);
        request.setCloseReason(CloseReason.REQUESTER_CANCELLED);
        outbox.enqueue(RequestEvents.cancelled(request), request.getId());
        return request;
    }

    // Back to CREATED, so the courier fields go back to their
    // "null until accepted" state. request.dropped tells the requester,
    // not request.cancelled: the reward must stay held for the next
    // courier (Order F5.2). The event names the courier who dropped, so
    // they are captured before the clear and passed in explicitly.
    @Transactional
    public Requests drop(Long requestId, Long callerId) {
        Requests request = transition(asCourier(requestId, callerId), OrderEvents.DROP);
        Long courierId = request.getCourierId();
        request.setCourierId(null);
        request.setAcceptedAt(null);
        outbox.enqueue(RequestEvents.dropped(request, courierId), request.getId());
        return request;
    }

    // EXPIRE: the deadline passed before collection; request.expired
    // releases the held reward to the requester.
    @Transactional
    public Requests expire(Long requestId) {
        Requests request = transition(requestId, OrderEvents.EXPIRE);
        request.setCloseReason(CloseReason.DEADLINE_PASSED);
        outbox.enqueue(RequestEvents.expired(request), request.getId());
        return request;
    }

    // COMPLETE: the item was handed over; request.completed tells the
    // Credit Service to transfer the held reward to the courier.
    @Transactional
    public Requests complete(Long requestId, Long callerId) {
        return completed(transition(asRequester(requestId, callerId), OrderEvents.COMPLETE));
    }

    // COMPLETE without the requester: the auto-complete sweep, an hour
    // after the courier arrived (F7.2). The same request.completed as a
    // manual confirmation, so the Credit Service pays the courier.
    @Transactional
    public Requests completeAutomatically(Long requestId) {
        return completed(transition(requestId, OrderEvents.COMPLETE));
    }

    private Requests completed(Requests request) {
        outbox.enqueue(RequestEvents.completed(request), request.getId());
        return request;
    }

    // RETRY SETTLEMENT (ADMIN / OWNER, DSP-10): the request's newest final
    // event is made unsent again with its original event ID, so the
    // publisher sends it once more; Notification deduplicates it and
    // Credit is idempotent. An active request has nothing to settle yet,
    // and a finished one without such an event (e.g. REJECTED: nothing was
    // held) has nothing to resend: both IllegalStateException (409).
    @Transactional
    public OutboxEvent retrySettlement(Long requestId) {
        Requests request = requestsRepository.findById(requestId).orElseThrow();
        if (!OrderStateMachine.isTerminal(request.getStatus())) {
            throw new IllegalStateException("Request " + requestId + " is not finished");
        }
        OutboxEvent event = outboxEvents
                .findFirstByRequestIdAndEventTypeInOrderByCreatedAtDesc(requestId, SETTLEMENT_EVENT_TYPES)
                .orElseThrow(() -> new IllegalStateException("Request " + requestId + " has no settlement event"));
        outboxEvents.markUnsent(event.getEventId());
        return event;
    }

    // The caller-supplied fields of a new request: the NOT NULL columns
    // must be present (blank text counts as missing), the reward at least
    // 1 credit (F1.1.3) and the deadline in the future (F1.1.2). Every
    // problem is reported in one message. supplierName is required here
    // too, though its column stays nullable (the author's decision).
    private static void validateNew(Requests request) {
        List<String> missing = new ArrayList<>();
        if (request.getRequesterId() == null) missing.add("requesterId");
        if (request.getSupplierId() == null) missing.add("supplierId");
        if (isBlank(request.getSupplierName())) missing.add("supplierName");
        if (isBlank(request.getDropoffLocation())) missing.add("dropoffLocation");
        if (isBlank(request.getItem())) missing.add("item");
        if (request.getCreditAmount() == null) missing.add("creditAmount");
        if (request.getDeadline() == null) missing.add("deadline");

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("Missing required fields: " + String.join(", ", missing));
        }
        if (request.getCreditAmount() != null && request.getCreditAmount() < 1) {
            problems.add("creditAmount must be at least 1");
        }
        if (request.getDeadline() != null && !request.getDeadline().isAfter(Instant.now())) {
            problems.add("deadline must be in the future");
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException(String.join("; ", problems));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Loads the request and moves it to the state the event leads to.
    private Requests transition(Long requestId, OrderEvents event) {
        return transition(requestsRepository.findById(requestId).orElseThrow(), event);
    }

    // Moves a loaded request to the state the event leads to; throws
    // IllegalStateException if the event is not allowed from its current
    // state. The change is written when the transaction commits, guarded
    // by the entity's @Version column.
    private static Requests transition(Requests request, OrderEvents event) {
        request.setStatus(OrderStateMachine.next(request.getStatus(), event));
        return request;
    }

    // The request, if the caller is its requester. Anyone else gets the
    // same NoSuchElementException as for an unknown id (404), so the
    // request's existence is not revealed. Checked before the state, so
    // the answer does not depend on where the request is in its life.
    private Requests asRequester(Long requestId, Long callerId) {
        Requests request = requestsRepository.findById(requestId).orElseThrow();
        if (!request.getRequesterId().equals(callerId)) {
            throw new NoSuchElementException();
        }
        return request;
    }

    // The request, if the caller is its assigned courier (none until
    // accepted, so nobody qualifies before that); otherwise as above.
    private Requests asCourier(Long requestId, Long callerId) {
        Requests request = requestsRepository.findById(requestId).orElseThrow();
        if (request.getCourierId() == null || !request.getCourierId().equals(callerId)) {
            throw new NoSuchElementException();
        }
        return request;
    }

}
