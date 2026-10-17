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
 * requester (the author's decision). DROP still publishes nothing.
 * 2026-10-18: accept refuses a courier who is the request's own
 * requester, answered 409 (the author's decision).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RequestsService {

    private final RequestsRepository requestsRepository;
    private final EventOutbox outbox;

    public RequestsService(RequestsRepository requestsRepository, EventOutbox outbox) {
        this.requestsRepository = requestsRepository;
        this.outbox = outbox;
    }

    // CREATE: the caller fills in the request's own fields; status and
    // createdAt are set here. The request waits in PENDING until the
    // Credit Service answers request.submitted.
    @Transactional
    public Requests create(Requests request) {
        request.setStatus(OrderStateMachine.INITIAL_STATE);
        request.setCreatedAt(Instant.now());
        Requests saved = requestsRepository.save(request);
        outbox.enqueue(RequestEvents.submitted(saved));
        return saved;
    }

    // RESERVE: the reward is held, so the request becomes visible to couriers.
    @Transactional
    public Requests reserve(Long requestId) {
        Requests request = transition(requestId, OrderEvents.RESERVE);
        outbox.enqueue(RequestEvents.created(request));
        return request;
    }

    // REJECT: the reward could not be reserved; the request ends here.
    @Transactional
    public Requests reject(Long requestId, String reason) {
        Requests request = transition(requestId, OrderEvents.REJECT);
        outbox.enqueue(RequestEvents.rejected(request, reason));
        return request;
    }

    // TIMEOUT: no reply in time. request.cancelled releases the reward
    // if the reservation happens after all.
    @Transactional
    public Requests timeout(Long requestId) {
        Requests request = transition(requestId, OrderEvents.TIMEOUT);
        outbox.enqueue(RequestEvents.cancelled(request));
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
        request.setCourierId(courierId);
        request.setAcceptedAt(Instant.now());
        return request;
    }

    @Transactional
    public Requests collect(Long requestId) {
        Requests request = transition(requestId, OrderEvents.COLLECT);
        request.setCollectedAt(Instant.now());
        return request;
    }

    // ARRIVE: the courier is at the drop-off location; the requester is
    // told through request.courier-arrived.
    @Transactional
    public Requests arrive(Long requestId) {
        Requests request = transition(requestId, OrderEvents.ARRIVE);
        request.setArrivedAt(Instant.now());
        outbox.enqueue(RequestEvents.courierArrived(request));
        return request;
    }

    // CANCEL: the requester withdraws before any courier accepted;
    // request.cancelled releases the held reward to them.
    @Transactional
    public Requests cancel(Long requestId) {
        Requests request = transition(requestId, OrderEvents.CANCEL);
        outbox.enqueue(RequestEvents.cancelled(request));
        return request;
    }

    // Back to CREATED, so the courier fields go back to their
    // "null until accepted" state. No request.cancelled here: the reward
    // must stay held for the next courier (Order F5.2).
    @Transactional
    public Requests drop(Long requestId) {
        Requests request = transition(requestId, OrderEvents.DROP);
        request.setCourierId(null);
        request.setAcceptedAt(null);
        return request;
    }

    // EXPIRE: the deadline passed before collection; request.expired
    // releases the held reward to the requester.
    @Transactional
    public Requests expire(Long requestId) {
        Requests request = transition(requestId, OrderEvents.EXPIRE);
        outbox.enqueue(RequestEvents.expired(request));
        return request;
    }

    // COMPLETE: the item was handed over; request.completed tells the
    // Credit Service to transfer the held reward to the courier.
    @Transactional
    public Requests complete(Long requestId) {
        Requests request = transition(requestId, OrderEvents.COMPLETE);
        outbox.enqueue(RequestEvents.completed(request));
        return request;
    }

    // Loads the request and moves it to the state the event leads to;
    // throws IllegalStateException if the event is not allowed from its
    // current state. The change is written when the transaction commits,
    // guarded by the entity's @Version column.
    private Requests transition(Long requestId, OrderEvents event) {
        Requests request = requestsRepository.findById(requestId).orElseThrow();
        request.setStatus(OrderStateMachine.next(request.getStatus(), event));
        return request;
    }

}
