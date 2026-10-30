/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the submit API's 409 refusal (ratings are immutable and
 * one-per-direction, #44), mapped to problem+json by
 * ProblemDetailAdvice.
 * Author review: [pending]
 */
package foc.rating.service;

/** The caller has already rated this request; ratings are immutable. */
public class AlreadyRatedException extends RuntimeException {

    public AlreadyRatedException() {
        super("You have already rated this request");
    }
}
