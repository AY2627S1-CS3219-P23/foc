/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the REST API's error mapping (docs/notification-service.md
 * "REST API contract", "Errors"): 400 problem+json for refused
 * parameters and bodies, 404 for a notification the caller doesn't
 * have — the format the web client reads. Scaffolded from
 * credit-service's ApiExceptionHandler.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Only the REST API's errors come here. Event-path failures never do:
 * they are retried or dead-lettered. A 401 is written by
 * {@code RestAuthEntryPoint} before a request reaches a controller.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	// a value the contract's own rules refuse: out of range
	@ExceptionHandler(InvalidQueryParameterException.class)
	public ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	// a parseable body missing what the contract requires (the PATCH's
	// read flag)
	@ExceptionHandler(InvalidRequestBodyException.class)
	public ProblemDetail handleInvalidRequestBody(InvalidRequestBodyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	// a body that is not JSON at all, or JSON of the wrong shape
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"request body must be JSON in the documented shape");
	}

	// a value that is not of the parameter's type at all: "abc" or 1.5
	// for a number
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		Class<?> required = ex.getRequiredType();
		String expected;
		if (Integer.class.equals(required) || int.class.equals(required)
				|| Long.class.equals(required) || long.class.equals(required)) {
			expected = "an integer";
		} else if (Boolean.class.equals(required) || boolean.class.equals(required)) {
			expected = "true or false";
		} else {
			expected = "a valid value";
		}
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getName() + " must be " + expected);
	}

	// the caller has no such notification — theirs or anyone's,
	// indistinguishably (D26)
	@ExceptionHandler(NotificationNotFoundException.class)
	public ProblemDetail handleNotFound(NotificationNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}
}
