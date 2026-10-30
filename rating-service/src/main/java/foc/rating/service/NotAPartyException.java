/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: the submit API's 403 refusal (caller is neither the requester
 * nor the courier), mapped to problem+json by ProblemDetailAdvice.
 * Author review: [pending]
 */
package foc.rating.service;

/** The caller is neither party of the completed request. */
public class NotAPartyException extends RuntimeException {

    public NotAPartyException() {
        super("Only a party of the request may rate or view its ratings");
    }
}
