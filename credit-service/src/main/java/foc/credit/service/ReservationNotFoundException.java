/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, PR #175 review (Leong Wei
 * Zhi).
 * Scope: the exception the request event handler throws for a
 * completion with no record. Retrying it, and the exception's name, are
 * the team's decision; the tool wrote the class and its message.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

/**
 * A {@code request.completed} found no record for its request: the
 * reservation has not been applied yet. Thrown by the request event
 * handler so the event is rolled back and retried, and dead-lettered
 * only after the maximum attempts (docs/credit-service.md D11).
 */
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String requestRef) {
        super("no reservation recorded for request " + requestRef);
    }
}
