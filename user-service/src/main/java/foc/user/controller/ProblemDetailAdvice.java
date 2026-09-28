/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: problem+json handlers shared by ProfileController and AdminController
       (PR #135 review): the UserNotFoundException handler, moved here from
       both controllers, and the 409 for a save that loses an optimistic
       locking race (409 problem+json per team decision).
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
}
