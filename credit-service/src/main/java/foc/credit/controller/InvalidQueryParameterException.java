/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: a query parameter the read API refuses (docs/credit-service.md
 * "Read API contract", "Errors"); ApiExceptionHandler answers it 400.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

/** A query parameter outside what the contract allows. Its message is shown to the caller. */
public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}
