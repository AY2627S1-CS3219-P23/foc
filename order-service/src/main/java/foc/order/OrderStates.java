/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: PENDING and REJECTED added to the author's enum for the saga
 * with the Credit Service (team design, docs/credit-service.md D7-D8);
 * the other constants are the author's.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: DISPUTED and RESOLVED
 * (team decision, docs/disputes.md). Author review: Ryan Ang, pending
 * pull request review.
 */
package foc.order;

public enum OrderStates{
    PENDING, // waiting for the Credit Service to reserve the reward
    CREATED,
    ACCEPTED,
    COLLECTED,
    ARRIVED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    REJECTED, // the reward could not be reserved; final
    DISPUTED, // the requester reported a problem, or the sweep escalated it; the reward stays held
    RESOLVED // an admin divided the held reward; final

}
