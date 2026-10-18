/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: copied from credit-service: a query parameter the REST API
 * refuses (docs/notification-service.md "REST API contract");
 * ApiExceptionHandler answers it 400.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

/** A query parameter outside what the contract allows. Its message is shown to the caller. */
public class InvalidQueryParameterException extends RuntimeException {

	public InvalidQueryParameterException(String message) {
		super(message);
	}
}
