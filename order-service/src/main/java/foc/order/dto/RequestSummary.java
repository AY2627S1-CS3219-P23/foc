/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: one request in a list (GET /requests, /requests/mine,
 * /users/{id}/requests). The trimmed field set is the author's decision;
 * GET /requests/{id} returns the full RequestDetail.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: closeReason, null
 * unless the request ended without completing (team decision: the admin
 * lists need it on each row). Author review: Ryan Ang, pending pull
 * request review.
 */
package foc.order.dto;

import foc.order.CloseReason;
import foc.order.OrderStates;
import foc.order.model.Requests;

import java.time.Instant;

public record RequestSummary(Long id, OrderStates status, CloseReason closeReason, String supplierName,
        String dropoffLocation, String item, Integer creditAmount, Instant deadline, Instant createdAt) {

    public static RequestSummary of(Requests r) {
        return new RequestSummary(r.getId(), r.getStatus(), r.getCloseReason(), r.getSupplierName(),
                r.getDropoffLocation(), r.getItem(), r.getCreditAmount(), r.getDeadline(), r.getCreatedAt());
    }
}
