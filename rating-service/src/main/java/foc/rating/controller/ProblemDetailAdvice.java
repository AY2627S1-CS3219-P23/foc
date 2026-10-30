/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a trimmed copy of user-service's ProblemDetailAdvice (the
 * problem+json reference implementation, per the #138 contract):
 * the invalid/unreadable-body, media-type and method handlers, plus
 * this service's three refusals and the unique-key race's 409.
 * Copied, not shared, by the author's decision.
 * Author review: [pending]
 */
package foc.rating.controller;

import java.util.Comparator;
import java.util.stream.Collectors;

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

import foc.rating.service.AlreadyRatedException;
import foc.rating.service.NotAPartyException;
import foc.rating.service.UnknownRequestException;

// the web client reads RFC 9457 problem+json error bodies
@RestControllerAdvice
class ProblemDetailAdvice {

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

    // two submits racing past the pre-check: the unique
    // (request_id, rater_id) key refuses the loser, same answer as the
    // pre-check. The rating FK also surfaces this way, but the 404 gate
    // fires before any insert can hit it.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDuplicateRating(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "You have already rated this request");
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
