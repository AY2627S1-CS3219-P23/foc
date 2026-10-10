/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the credit operations interface, with the signatures the author
 * supplied; the comments point at docs/credit-service.md. No
 * implementation yet (see NotImplementedCreditOperations).
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

    /** Reserves a request's reward from the requester, or refuses (D8). */
    ReserveResult reserve(String requestRef, String requesterId, int amount);

    /** Moves a request's held credits to the courier (D9). */
    void transfer(String requestRef, String courierId);

    /** Returns a request's held credits to the requester (D9). */
    void release(String requestRef, String requesterId);
}
