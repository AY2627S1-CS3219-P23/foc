/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: builds the saga's request events from a stored request. The
 * field mapping (pickupLocation = supplierName, note = item, reward =
 * creditAmount, ids as decimal strings, parties = the requester) and
 * correlationId = the request id are the author's decisions; the
 * records are foc-contracts'.
 * 2026-10-16: request.courier-arrived for the ARRIVE transition, same
 * mapping (parties = the requester only, the author's decision).
 * 2026-10-17: request.completed for the COMPLETE transition, so the
 * Credit Service pays the courier; parties = the requester and the
 * courier (the author's decision).
 * Same day: request.cancelled reused for CANCEL (parties = the
 * requester), and request.expired for EXPIRE (parties = the requester,
 * plus the courier when one had accepted; the author's decisions).
 * 2026-10-18: request.accepted and request.collected, parties = the
 * requester and the courier (the author's decision).
 * Same day, Claude Code (Fable 5): request.dropped for the DROP
 * transition (parties = the requester only, the author's decision via
 * options Q&A); pending pull request review.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.contracts.events.request.CourierArrived;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCollected;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestCreated;
import foc.contracts.events.request.RequestDropped;
import foc.contracts.events.request.RequestExpired;
import foc.contracts.events.request.RequestRejected;
import foc.contracts.events.request.RequestSubmitted;
import foc.order.model.Requests;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The request events this service publishes, built from a stored request. */
final class RequestEvents {

    static final String PRODUCER = "order-service";

    private RequestEvents() {
    }

    // request.submitted: saga step 1, asks the Credit Service to reserve the reward
    static RequestSubmitted submitted(Requests r) {
        return new RequestSubmitted(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), r.getSupplierName(), r.getDropoffLocation(), r.getItem(),
                r.getCreditAmount());
    }

    // request.created: saga step 3a, the reward is reserved
    static RequestCreated created(Requests r) {
        return new RequestCreated(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), r.getSupplierName(), r.getDropoffLocation(), r.getItem());
    }

    // request.rejected: saga step 3b, with the Credit Service's reason
    static RequestRejected rejected(Requests r, String reason) {
        return new RequestRejected(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), r.getSupplierName(), r.getDropoffLocation(), r.getItem(),
                reason);
    }

    // request.cancelled: a pending request timed out, or the requester
    // cancelled before any courier accepted, so there is never a courier
    static RequestCancelled cancelled(Requests r) {
        return new RequestCancelled(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.accepted: a courier took the request
    static RequestAccepted accepted(Requests r) {
        return new RequestAccepted(newEventId(), Instant.now(), PRODUCER, correlationId(r), requesterAndCourier(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.collected: the courier has the item from the supplier
    static RequestCollected collected(Requests r) {
        return new RequestCollected(newEventId(), Instant.now(), PRODUCER, correlationId(r), requesterAndCourier(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.dropped: the courier withdrew after accepting; the request
    // reopens and the reward stays held (F5.2), so only the requester is
    // told. Built while the courier fields are still set.
    static RequestDropped dropped(Requests r) {
        return new RequestDropped(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.courier-arrived: the courier is at the drop-off location,
    // so the requester can come and collect the item
    static CourierArrived courierArrived(Requests r) {
        return new CourierArrived(newEventId(), Instant.now(), PRODUCER, correlationId(r), parties(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.completed: the Credit Service transfers the held reward to
    // the courier. Both users are told, unlike the other events.
    static RequestCompleted completed(Requests r) {
        return new RequestCompleted(newEventId(), Instant.now(), PRODUCER, correlationId(r),
                requesterAndCourier(r),
                id(r.getId()), id(r.getRequesterId()), id(r.getCourierId()), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem());
    }

    // request.expired: the deadline passed before collection. The record
    // has no courierId, so a courier who had accepted is only in parties.
    static RequestExpired expired(Requests r) {
        List<String> parties = new ArrayList<>(parties(r));
        if (r.getCourierId() != null) {
            parties.add(id(r.getCourierId()));
        }
        return new RequestExpired(newEventId(), Instant.now(), PRODUCER, correlationId(r), List.copyOf(parties),
                id(r.getId()), id(r.getRequesterId()), r.getSupplierName(), r.getDropoffLocation(), r.getItem());
    }

    private static String correlationId(Requests r) {
        return id(r.getId());
    }

    private static List<String> requesterAndCourier(Requests r) {
        return List.of(id(r.getRequesterId()), id(r.getCourierId()));
    }

    private static List<String> parties(Requests r) {
        return List.of(id(r.getRequesterId()));
    }

    private static String id(Long id) {
        return id == null ? null : id.toString();
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
