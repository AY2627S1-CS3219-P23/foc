/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the return type of CreditOperations.reserve; the three values
 * are the author's choice.
 * PR #168 review (Leong Wei Zhi): REJECTED split into its two causes, so
 * the result carries the reason instead of the handler working it out
 * (team decision among the reviewer's options).
 * 2026-10-18, issue #165 (reviewer's suggestion): the ALREADY_* values
 * name what an existing record holds, so its reply can be sent again.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

/** What {@link CreditOperations#reserve} did. */
public enum ReserveResult {
    RESERVED,
    // both refusals record the request as REJECTED; the result says why,
    // and the reply's reason follows it
    REJECTED_INSUFFICIENT_CREDITS,
    // a reward below 1 (Credit F1.2.1)
    REJECTED_INVALID_AMOUNT,
    // a redelivery of a request already decided: nothing changes, and the
    // recorded answer is sent again, so a lost reply can be recovered
    // (issue #165, reviewer's suggestion)
    ALREADY_RESERVED,
    ALREADY_REJECTED_INSUFFICIENT_CREDITS,
    ALREADY_REJECTED_INVALID_AMOUNT,
    // the request is settled (TRANSFERRED, or RELEASED, including the
    // amount-0 record from a cancel that arrived first); nothing changed
    // and no reply is owed
    DUPLICATE
}
