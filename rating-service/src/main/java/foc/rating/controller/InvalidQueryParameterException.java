/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #253.
 * Scope: copied from credit-service's InvalidQueryParameterException
 * (copied, not shared, by the author's decision); ProblemDetailAdvice
 * answers it 400.
 * Author review: [pending]
 */
package foc.rating.controller;

/** A query parameter outside what the contract allows. Its message is shown to the caller. */
class InvalidQueryParameterException extends RuntimeException {

    InvalidQueryParameterException(String message) {
        super(message);
    }
}
