/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the credit_reservation.status values, from V1__baseline.sql's
 * CHECK constraint (docs/credit-service.md "Schema").
 * 2026-10-25, issue #235: RESOLVED, from V4 (team decision, DSP-4).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.entity;

/** What has been decided and done for one request's reservation (D3). */
public enum ReservationStatus {
    HELD,
    REJECTED,
    TRANSFERRED,
    RELEASED,
    // a dispute settled by dividing the reward between courier and requester (DSP-4)
    RESOLVED
}
