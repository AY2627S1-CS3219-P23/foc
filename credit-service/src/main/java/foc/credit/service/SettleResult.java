/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the return type of CreditOperations.transfer and release (PR #166
 * review, Leong Wei Zhi); the enum and its three values are the reviewer's
 * suggestion, chosen by the author.
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
    // the request's record conflicts with the operation; nothing changed,
    // and the listener dead-letters the event (D11)
    INVALID_STATE
}
