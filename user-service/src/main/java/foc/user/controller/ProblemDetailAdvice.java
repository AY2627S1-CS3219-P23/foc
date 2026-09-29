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
*/

package foc.user.controller;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
