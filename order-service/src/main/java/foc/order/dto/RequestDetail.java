/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: one request in full (GET /requests/{id}): every column but the
 * optimistic-lock version (the author's decision: full detail).
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: closeReason, null
 * unless the request ended without completing (team decision). Author
 * review: Ryan Ang, pending pull request review.
 */
package foc.order.dto;

import foc.order.CloseReason;
import foc.order.OrderStates;
import foc.order.model.Requests;

import java.time.Instant;

public record RequestDetail(Long id, OrderStates status, CloseReason closeReason, Long requesterId,
        Long courierId, Long supplierId, String supplierName, String dropoffLocation, String item,
        Integer creditAmount, Instant deadline, Instant createdAt, Instant acceptedAt, Instant collectedAt,
        Instant arrivedAt) {

    public static RequestDetail of(Requests r) {
        return new RequestDetail(r.getId(), r.getStatus(), r.getCloseReason(), r.getRequesterId(),
                r.getCourierId(), r.getSupplierId(), r.getSupplierName(), r.getDropoffLocation(), r.getItem(),
                r.getCreditAmount(), r.getDeadline(), r.getCreatedAt(), r.getAcceptedAt(), r.getCollectedAt(),
                r.getArrivedAt());
    }
}
