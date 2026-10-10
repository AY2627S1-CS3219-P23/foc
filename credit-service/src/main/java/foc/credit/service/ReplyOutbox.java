/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the reply outbox interface, with the signature the author
 * supplied; the comments point at docs/credit-service.md. No
 * implementation yet (see NotImplementedReplyOutbox).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import foc.contracts.events.core.DomainEvent;

/** Where a credit operation leaves its reply for the publisher (D10). */
public interface ReplyOutbox {

    /**
     * Stores the reply for publishing. Called inside the caller's
     * transaction, so the reply commits with the balance change or not
     * at all. An implementation requires that transaction (propagation
     * MANDATORY), so a call outside one fails instead of committing a
     * reply on its own.
     */
    void enqueue(DomainEvent reply);
}
