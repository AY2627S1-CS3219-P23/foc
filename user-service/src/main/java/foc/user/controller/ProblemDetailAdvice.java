/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: problem+json handlers shared by ProfileController and AdminController
       (PR #135 review): the UserNotFoundException handler, moved here from
       both controllers, and the 409 for a save that loses an optimistic
       locking race (409 problem+json per team decision).
       2026-09-29 (issue #147): the invalid-body and unreadable-body
       handlers moved here from AuthController, so every controller
       (including OwnerSetupController, which had none) reports broken
       rules one sentence per field (team decision; #138 contract).
Author review: Ryan to review via the PR.
2026-09-30 (Claude Code, Fable 5), issue #92: the three OTP handlers moved
here from AuthController — the account-update routes fail the same ways
sign-up's OTP does, and one handler set keeps the statuses and details in
lockstep. They now attach problem+json type URIs (ProblemTypes; owner
decision via options Q&A: machine-readable types in scope, so the SPA can
stop string-matching detail sentences), and a ResponseStatusException
passthrough renders typed bodies (uniqueness refusals) for controllers
without a local one.
2026-10-05 (Claude Code, Fable 5), PR #157 Copilot review: the
attempts-exceeded 429 now carries Retry-After — the exhausted row
survives as spent, so there is a real wait (the resend cooldown) to
quote before a replacement code may be requested.
*/

package foc.user.controller;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.exception.UserNotFoundException;

// the web client reads RFC 9457 problem+json error bodies
@RestControllerAdvice
class ProblemDetailAdvice {

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // another request saved the same user first (User's @Version check)
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The account was changed by another request; reload and try again");
    }

    // every broken rule, sorted by field so the message is stable (moved
    // from AuthController: one validation format for every endpoint)
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

    // no body, broken JSON, or a value of the wrong type (e.g. an unknown role)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    // the OTP failures, moved from AuthController (issue #92): sign-up and
    // the account-update routes fail the same ways, with the same bodies.
    // The exception names the type (wrong code / expired / no code yet /
    // no pending change), the status stays 400
    @ExceptionHandler(OtpVerificationException.class)
    ProblemDetail handleOtpVerification(OtpVerificationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setType(e.type());
        return problem;
    }

    // 429 like the login lockout (issue #145 precedent). The exhausted
    // row now survives as spent and its replacement waits out the resend
    // cooldown (PR #157 reviews), so there IS a wait to quote: Retry-After
    // carries it, 0 meaning a fresh code can be requested right away
    @ExceptionHandler(OtpAttemptsExceededException.class)
    ResponseEntity<ProblemDetail> handleOtpAttemptsExceeded(OtpAttemptsExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setType(ProblemTypes.OTP_ATTEMPTS_EXCEEDED);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
            .body(problem);
    }

    // 429 with Retry-After, the lockout's shape: here waiting is exactly
    // the remedy, and the wait is seconds, so the SPA can count it down
    @ExceptionHandler(OtpResendTooSoonException.class)
    ResponseEntity<ProblemDetail> handleOtpResendTooSoon(OtpResendTooSoonException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setType(ProblemTypes.OTP_RESEND_COOLDOWN);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
            .body(problem);
    }

    // uniqueness refusals and other typed ResponseStatusExceptions reach
    // controllers that have no local passthrough (AuthController keeps its
    // own — local wins there, same output); returning the exception's own
    // body keeps the type it was built with
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException e) {
        return e.getBody();
    }
}
