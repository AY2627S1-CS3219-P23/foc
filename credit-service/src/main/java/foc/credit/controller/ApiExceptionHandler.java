/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the "Error mapping" component of docs/credit-service.md: the
 * read API's invalid query parameters answered 400 problem+json, the
 * error format the web client reads ("Read API contract", "Errors").
 * 2026-10-14, PR #189 review (Leong Wei Zhi): a type mismatch on a
 * parameter that is neither an instant nor an integer reads "must be a
 * valid value", per the reviewer's comment.
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
        Class<?> required = ex.getRequiredType();
        String expected;
        if (OffsetDateTime.class.equals(required)) {
            expected = "a full ISO-8601 instant with an offset, such as 2026-10-10T08:00:00Z";
        } else if (Integer.class.equals(required) || int.class.equals(required)) {
            expected = "an integer";
        } else {
            expected = "a valid value";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getName() + " must be " + expected);
    }
}
