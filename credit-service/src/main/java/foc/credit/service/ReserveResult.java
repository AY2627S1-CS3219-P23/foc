/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the return type of CreditOperations.reserve; the three values
 * are the author's choice.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

/** What {@link CreditOperations#reserve} did. */
public enum ReserveResult {
    RESERVED,
    REJECTED,
    // the request already has a record, including the amount-0 RELEASED
    // one from a cancel that arrived first; nothing changed
    DUPLICATE
}
