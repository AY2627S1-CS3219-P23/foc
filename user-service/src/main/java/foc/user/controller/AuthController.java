/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: POST /auth/signup (issue #87) and POST /auth/login (issues #89/#90),
       the routes from design doc §3's route table, replacing PR #139's
       user-auth server. Errors are problem+json with the exact reason
       (design doc: "400 + exact reason"); the handlers are local to this
       controller, so other controllers' error bodies are unchanged (#138).
       PR #141 review: the lost-race-to-401 mapping now covers only login,
       so other /auth routes keep ProblemDetailAdvice's 409.
       2026-09-29, Claude Code (Fable 5), PR #142: AccountLockedException
       mapped to 429 problem+json (issue #145 decided by the author — the
       lockout is deliberately distinguishable from other failures).
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.LoginRequest;
import foc.user.dto.LoginResponse;
import foc.user.dto.SignupRequest;
import foc.user.dto.UserResponse;
import foc.user.exception.AccountLockedException;
import foc.user.exception.LoginFailedException;
import foc.user.service.AuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            return authService.login(request);
        } catch (OptimisticLockingFailureException e) {
            // two logins to one account at once: the loser's counter update
            // fails the @Version check at commit. Answered like any failed
            // login, not ProblemDetailAdvice's 409, which would reveal that
            // the account exists
            throw new LoginFailedException();
        }
    }

    @ExceptionHandler(LoginFailedException.class)
    public ProblemDetail handleLoginFailed(LoginFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 429, not 401: the lockout is deliberately distinguishable (issue
    // #145 decision) so the UI can show the wireframe's lockout message
    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException e) {
        return e.getBody();
    }

    // every broken rule, sorted by field so the message is stable
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException e) {
        String reasons = e.getBindingResult().getFieldErrors().stream()
            .sorted((a, b) -> a.getField().compareTo(b.getField()))
            .map(error -> error.getDefaultMessage())
            .distinct()
            // some messages end in a full stop and some don't
            .map(message -> message.endsWith(".") ? message : message + ".")
            .collect(Collectors.joining(" "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, reasons);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }
}
