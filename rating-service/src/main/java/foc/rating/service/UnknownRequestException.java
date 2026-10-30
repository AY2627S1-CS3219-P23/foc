/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the submit API's 404 refusal (unknown or not-yet-completed
 * request), mapped to problem+json by ProblemDetailAdvice.
 * Author review: [pending]
 */
package foc.rating.service;

/**
 * The request is unknown here: order-service never completed it, or the
 * completion event has not arrived yet. One answer for both, so the API
 * does not reveal which.
 */
public class UnknownRequestException extends RuntimeException {

    public UnknownRequestException() {
        super("No completed request with that id");
    }
}
