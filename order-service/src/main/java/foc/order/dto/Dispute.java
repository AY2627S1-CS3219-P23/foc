/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: a request's dispute as RequestSummary and
 * RequestDetail return it. The field set and its place on both DTOs are
 * the team's decision (#239 answers).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order.dto;

import foc.order.DisputeCategory;
import foc.order.model.RequestDispute;

import java.time.Instant;

// The resolution fields are null until an admin resolves the dispute.
public record Dispute(DisputeCategory category, String disputeNote, Instant raisedAt, boolean autoEscalated,
        Instant resolvedAt, Long resolvedBy, Integer courierAmount, Integer requesterAmount,
        String resolutionNote) {

    // null for a request that was never disputed
    public static Dispute of(RequestDispute d) {
        if (d == null) {
            return null;
        }
        return new Dispute(d.getCategory(), d.getDisputeNote(), d.getRaisedAt(), d.isAutoEscalated(),
                d.getResolvedAt(), d.getResolvedBy(), d.getCourierAmount(), d.getRequesterAmount(),
                d.getResolutionNote());
    }
}
