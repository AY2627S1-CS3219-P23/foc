/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the credit operations interface, with the signatures the author
 * supplied; the comments point at docs/credit-service.md. No
 * implementation yet (see NotImplementedCreditOperations).
 * PR #166 review (Leong Wei Zhi): transfer and release return a
 * SettleResult instead of void (the author's choice of the reviewer's
 * options).
 * PR #168 review (Leong Wei Zhi): reserve's comment states the
 * reward-below-1 rule and the two refusals its result tells apart.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import foc.credit.entity.CreditAccount;

/**
 * The credit operations of docs/credit-service.md ("Idempotent credit
 * operations service"). The listener and the REST API call this; the
 * outcomes per reservation status are the table in "How each event is
 * handled".
 */
public interface CreditOperations {

    /** The user's account, created on first sight of the ID (D6). */
    CreditAccount getOrCreate(String userId);

    /**
     * Reserves a request's reward from the requester, or refuses (D8).
     * Gets or creates the requester's account first (D6).
     *
     * <p>An amount below 1 is refused whatever the balance (Credit
     * F1.2.1): {@link ReserveResult#REJECTED_INVALID_AMOUNT}, never a
     * reservation of 0. An amount above the available balance is
     * {@link ReserveResult#REJECTED_INSUFFICIENT_CREDITS}. Both record
     * the request as {@code REJECTED}.
     */
    ReserveResult reserve(String requestRef, String requesterId, int amount);

    /**
     * Moves a request's held credits to the courier (D9). The result says
     * whether it did, had already, or could not for the request's status.
     */
    SettleResult transfer(String requestRef, String courierId);

    /**
     * Returns a request's held credits to the requester (D9). With no
     * reservation for the request, gets or creates the requester's account
     * (D6) and records the request as released with amount 0, so a late
     * reserve is ignored. The result is as for {@link #transfer}.
     */
    SettleResult release(String requestRef, String requesterId);
}
