/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the body-shaped sibling of InvalidQueryParameterException: a
 * parseable request body missing what the contract requires (the
 * PATCH's read flag); ApiExceptionHandler answers it 400.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

/** A request body outside what the contract allows. Its message is shown to the caller. */
public class InvalidRequestBodyException extends RuntimeException {

	public InvalidRequestBodyException(String message) {
		super(message);
	}
}
