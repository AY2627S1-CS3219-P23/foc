/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: UserNotFoundException handler moved here from ProfileController and
       AdminController, which had identical copies (PR #135 review).
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import foc.user.exception.UserNotFoundException;

@RestControllerAdvice
class UserNotFoundAdvice {

    // the web client reads RFC 9457 problem+json error bodies
    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
