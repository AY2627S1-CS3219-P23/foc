/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the credit operations interface, with the signatures the author
 * supplied; the comments point at docs/credit-service.md. Implemented
 * by CreditOperationsService (2026-10-11, issue #169).
 * PR #166 review (Leong Wei Zhi): transfer and release return a
 * SettleResult instead of void (the author's choice of the reviewer's
 * options).
 * PR #168 review (Leong Wei Zhi): reserve's comment states the
 * reward-below-1 rule and the two refusals its result tells apart.
 * PR #175 review (Leong Wei Zhi): transfer's comment states the
 * no-record result (team decision).
 * PR #212 review (Leong Wei Zhi): reserve's comment states its result
 * per existing record status (reviewer's suggestion).
 * 2026-10-25, issue #235: resolve, the split settlement of a resolved
 * dispute (team decisions DSP-4, DSP-8, DSP-13); reserve on a RESOLVED
 * record is DUPLICATE (team decision).
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
     *
     * <p>With a record already there, nothing is written and the result
     * is what the record holds: {@code HELD} is
     * {@link ReserveResult#ALREADY_RESERVED}; {@code REJECTED} is
     * {@link ReserveResult#ALREADY_REJECTED_INVALID_AMOUNT} for a recorded
     * amount below 1, otherwise
     * {@link ReserveResult#ALREADY_REJECTED_INSUFFICIENT_CREDITS};
     * {@code TRANSFERRED}, {@code RELEASED} or {@code RESOLVED} is
     * {@link ReserveResult#DUPLICATE}.
     */
    ReserveResult reserve(String requestRef, String requesterId, int amount);

    /**
     * Moves a request's held credits to the courier (D9). The result says
     * whether it did, had already, or could not for the request's status.
     * With no record for the request it is
     * {@link SettleResult#NO_RESERVATION} and nothing is written. A null
     * or blank courier is {@link SettleResult#INVALID_STATE}, whatever
     * the record.
     */
    SettleResult transfer(String requestRef, String courierId);

    /**
     * Returns a request's held credits to the requester (D9). With no
     * reservation for the request, gets or creates the requester's account
     * (D6) and records the request as released with amount 0, so a late
     * reserve is ignored. The result is as for {@link #transfer}, never
     * {@link SettleResult#NO_RESERVATION}. With no reservation and a null
     * or blank requester it is {@link SettleResult#INVALID_STATE}: there
     * is no account for the record to reference.
     */
    SettleResult release(String requestRef, String requesterId);

    /**
     * Settles a resolved dispute by dividing a request's held credits
     * (DSP-4): {@code courierAmount} to the courier as a new lot,
     * {@code requesterAmount} back to the requester's lots, latest expiry
     * first. A share of 0 writes no history row for that side.
     *
     * <p>With no record it is {@link SettleResult#NO_RESERVATION} and
     * nothing is written, as for {@link #transfer}. A {@code RESOLVED}
     * record with the same courier and split is
     * {@link SettleResult#DUPLICATE}; a different courier or split, or
     * any other status, is {@link SettleResult#INVALID_STATE} (DSP-13).
     * So is a null or blank courier, a null or negative share, whatever
     * the record, and shares that do not add up to the held amount
     * (DSP-8).
     */
    SettleResult resolve(String requestRef, String courierId, Integer courierAmount, Integer requesterAmount);
}
