/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: one request in a list (GET /requests, /requests/mine,
 * /users/{id}/requests). The trimmed field set is the author's decision;
 * GET /requests/{id} returns the full RequestDetail.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.dto;

import foc.order.OrderStates;
import foc.order.model.Requests;

import java.time.Instant;

public record RequestSummary(Long id, OrderStates status, String supplierName, String dropoffLocation,
        String item, Integer creditAmount, Instant deadline, Instant createdAt) {

    public static RequestSummary of(Requests r) {
        return new RequestSummary(r.getId(), r.getStatus(), r.getSupplierName(), r.getDropoffLocation(),
                r.getItem(), r.getCreditAmount(), r.getDeadline(), r.getCreatedAt());
    }
}
