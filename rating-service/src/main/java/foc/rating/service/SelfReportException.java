/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report API's 400 refusal (reporting oneself), mapped to
 * problem+json by ProblemDetailAdvice; NotAPartyException's shape.
 * Author review: [pending]
 */
package foc.rating.service;

/** The caller named themselves as the reported user. */
public class SelfReportException extends RuntimeException {

    public SelfReportException() {
        super("You cannot report yourself");
    }
}
