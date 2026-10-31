/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a trimmed copy of user-service's ProblemDetailAdvice (the
 * problem+json reference implementation, per the #138 contract):
 * the invalid/unreadable-body, media-type and method handlers, plus
 * this service's three refusals and the unique-key race's 409.
 * Copied, not shared, by the author's decision.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #272 review (claude[bot]): the
 * 409 answers only the rating_once_per_rater constraint (matched by
 * name through the cause chain); any other integrity violation is a
 * bug and stays a 500 problem.
 * Author review: [pending]
 * Same day, same reviewer: the 500 branch logs the violation at
 * ERROR — a handled exception never reaches Spring's default error
 * logging, so without this the bug would leave no trace.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #253: the two query-parameter
 * 400s the profile reads need (credit-service's ApiExceptionHandler
 * precedent, trimmed to the int case).
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #254: the self-report 400;
 * an unknown reason already lands in the unreadable-body 400.
 * Author review: [pending]
 */
package foc.rating.controller;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import foc.rating.service.AlreadyRatedException;
import foc.rating.service.NotAPartyException;
import foc.rating.service.SelfReportException;
import foc.rating.service.UnknownRequestException;

// the web client reads RFC 9457 problem+json error bodies
@RestControllerAdvice
class ProblemDetailAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

    @ExceptionHandler(UnknownRequestException.class)
    ProblemDetail handleUnknownRequest(UnknownRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NotAPartyException.class)
    ProblemDetail handleNotAParty(NotAPartyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(AlreadyRatedException.class)
    ProblemDetail handleAlreadyRated(AlreadyRatedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // naming oneself as the reported user (#254): a contract refusal,
    // not a permission one, so 400 rather than 403
    @ExceptionHandler(SelfReportException.class)
    ProblemDetail handleSelfReport(SelfReportException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // two submits racing past the pre-check: the unique
    // (request_id, rater_id) key refuses the loser, same answer as the
    // pre-check. Only that constraint means "duplicate rating" — any
    // other violation (the outbox primary key, the stars check, the FK)
    // is a bug this handler must not dress up as a 409, so it stays a
    // 500 problem (PR #272 review).
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException e) {
        if (isDuplicateRating(e)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "You have already rated this request");
        }
        // a handled exception never reaches Spring's default error
        // logging, so the bug must be logged here or nowhere
        log.error("Unexpected integrity violation", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
    }

    // the constraint's name appears in the driver message on both
    // PostgreSQL ("rating_once_per_rater") and H2 (the index it builds
    // for the constraint keeps the name as a prefix), so the message
    // walk works on the test and production databases alike
    private static boolean isDuplicateRating(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("rating_once_per_rater")) {
                return true;
            }
        }
        return false;
    }

    // every broken rule, sorted by field so the message is stable
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException e) {
        String reasons = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .distinct()
                // some messages end in a full stop and some don't
                .map(message -> message.endsWith(".") ? message : message + ".")
                .collect(Collectors.joining(" "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, reasons);
    }

    // no body, broken JSON, or a value of the wrong type
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    // GET /ratings/mine without its requestId: problem+json, not the
    // /error fallback
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.getParameterName() + " is required");
    }

    // a value the contract's own rules refuse: page before 0, size out
    // of bounds
    @ExceptionHandler(InvalidQueryParameterException.class)
    ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // a value that is not of the parameter's type at all ("abc" or 1.5
    // for page): problem+json, not the /error fallback
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> required = e.getRequiredType();
        String expected = Integer.class.equals(required) || int.class.equals(required)
                ? "an integer"
                : "a valid value";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getName() + " must be " + expected);
    }

    // a body that isn't JSON (or has no Content-Type): Spring's own
    // problem body, and its Accept header, which names the types the
    // endpoint takes
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(e.getBody());
    }

    // a method the path doesn't take (e.g. GET /ratings): Spring's own
    // problem body, and its Allow header, which names the methods it does
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(e.getBody());
    }
}
