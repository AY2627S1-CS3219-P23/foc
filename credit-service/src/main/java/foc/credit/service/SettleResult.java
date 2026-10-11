/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the return type of CreditOperations.transfer and release (PR #166
 * review, Leong Wei Zhi); the enum and its three values are the reviewer's
 * suggestion, chosen by the author.
 * 2026-10-11, PR #175 review (Leong Wei Zhi): NO_RESERVATION, split
 * from INVALID_STATE (team decision); INVALID_STATE also covers an
 * event missing a field (team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

/**
 * What {@link CreditOperations#transfer} or {@link CreditOperations#release}
 * did, so the listener can pick the outcome of docs/credit-service.md
 * ("How each event is handled").
 */
public enum SettleResult {
    SETTLED,
    // the request was already settled this way; nothing changed
    DUPLICATE,
    // the record conflicts with the operation, or the event is missing a
    // field; nothing changed, and the listener dead-letters the event (D11)
    INVALID_STATE,
    // a transfer found no record for the request: its reservation has not
    // been applied yet; nothing changed, and the event is retried (D11)
    NO_RESERVATION
}
