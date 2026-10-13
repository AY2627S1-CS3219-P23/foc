/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the "Error mapping" component of docs/credit-service.md: the
 * read API's invalid query parameters answered 400 problem+json, the
 * error format the web client reads ("Read API contract", "Errors").
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.controller;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Only the read API's errors come here. Event-path failures never do:
 * they are refused with a reply or dead-lettered. A 401 is written by
 * {@code RestAuthEntryPoint} before a request reaches a controller.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    // a value the contract's own rules refuse: out of range, or two
    // parameters that contradict each other
    @ExceptionHandler(InvalidQueryParameterException.class)
    public ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // a value that is not of the parameter's type at all: "abc" or 1.5
    // for a number, a date alone or an instant with no offset
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = OffsetDateTime.class.equals(ex.getRequiredType())
            ? "a full ISO-8601 instant with an offset, such as 2026-10-10T08:00:00Z"
            : "an integer";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getName() + " must be " + expected);
    }
}
