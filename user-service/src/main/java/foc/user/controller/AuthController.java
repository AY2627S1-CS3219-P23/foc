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
       2026-09-29 (issue #147): the invalid-body and unreadable-body
       handlers moved to ProblemDetailAdvice, shared by every controller
       (team decision).
       2026-09-29, Claude Code (Fable 5), PR #142: AccountLockedException
       mapped to 429 problem+json (issue #145 decided by the author — the
       lockout is deliberately distinguishable from other failures).
       2026-09-29, Claude Code (Opus 5), issue #146: the 429 carries a
       Retry-After header, and the lost-race path now answers with the
       wrong-password message (the mapping used to exist to hide that the
       account exists, which the new messages no longer do).
       2026-09-29, Claude Code (Fable 5), issue #88: sign-up now answers
       202 (a code was emailed; no account yet) and POST /auth/signup/verify
       is what creates the account (201) — design doc §3's insert-after-
       verify flow. Wrong/expired codes are 400, exhausted attempts 429
       (no Retry-After: there is no wait, the remedy is a fresh sign-up).
       Both routes sit under the /auth/** permit in SecurityConfig.
       2026-09-29, Claude Code (Opus 5), PR #150 author review: sign-up
       gained two refusals from the review of repeat sign-ups — 409 when
       the email has a live pending sign-up that isn't the caller's own
       request (which used to be silently merged, a hijack), and 429 with
       Retry-After when a resend is inside the cooldown.
       2026-10-02, Claude Code (Opus 5.5), issue #154: the
       ResponseStatusException handler moved to ProblemDetailAdvice.
       2026-10-11, Claude Code (Fable 5), issue #151: POST /auth/logout —
       denylists the caller's jti in Redis until the token's exp (team
       decisions, Leong Wei Zhi 2026-10-11 options Q&A: Redis-only
       denylist enforced at the API gateway; best-effort 204 even when
       the Redis write fails).
Author review: Leong Wei Zhi to review via the PR.
2026-09-30 (Claude Code, Fable 5), issue #92: the three OTP handlers moved
to ProblemDetailAdvice, shared with the account-update routes (same statuses,
same details; they now also carry problem+json type URIs).
2026-10-20 (Claude Code, Fable 5), issue #177, on the owner-frontend
branch (xiuxi): POST /auth/signup-owner — the owner-setup screen's
sign-up, replacing the curl-only POST /auth/setup-owner. Same 202 shape
as /auth/signup (the OTP flow is shared; /auth/signup/verify finishes
both), plus 403 for a wrong setup key and 503 when none is configured.
Consolidation into AuthService chosen by Leong Wei Zhi via options Q&A
over the branch's separate controller/service pair.
*/

package foc.user.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.LoginRequest;
import foc.user.dto.LoginResponse;
import foc.user.dto.SignupOwnerRequest;
import foc.user.dto.SignupRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.exception.AccountLockedException;
import foc.user.exception.LoginFailedException;
import foc.user.security.JwtVerifier;
import foc.user.security.TokenDenylist;
import foc.user.service.AuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenDenylist tokenDenylist;

    public AuthController(AuthService authService, TokenDenylist tokenDenylist) {
        this.authService = authService;
        this.tokenDenylist = tokenDenylist;
    }

    // 202, not 201: nothing is created yet — a code was emailed and the
    // account waits on /auth/signup/verify. Repeating the call resends
    // (a fresh code); there is no separate resend route
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    // sign-up for the owner-setup screen (issue #177): the same 202/OTP
    // flow with the setup key checked first — 403 wrong, 503 none
    // configured — and OWNER stamped on the pending row. Verification is
    // /auth/signup/verify, shared with plain sign-up
    @PostMapping(value = "/signup-owner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SignupResponse signupOwner(@Valid @RequestBody SignupOwnerRequest request) {
        return authService.signupOwner(request);
    }

    @PostMapping(value = "/signup/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse verifySignup(@Valid @RequestBody SignupVerifyRequest request) {
        return authService.verifySignup(request);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            return authService.login(request);
        } catch (OptimisticLockingFailureException e) {
            // two logins to one account at once: the loser's counter update
            // fails the @Version check at commit. Answered as a failed
            // login, not ProblemDetailAdvice's 409, which is no answer to
            // give someone typing a password. The count it lost is the
            // count it can't quote, so this one carries no tally
            throw LoginFailedException.wrongPassword();
        }
    }

    // 204 always (idempotent, and best-effort by team decision G9: a failed
    // Redis write is logged by TokenDenylist, not surfaced — the token then
    // just lives out its remaining exp). The route is authenticated()
    // in SecurityConfig, so the filter has already verified the token and
    // stored it as the authentication's details
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        if (authentication.getDetails() instanceof JwtVerifier.VerifiedToken token
                && token.jti() != null) {
            tokenDenylist.revoke(token.jti(), token.expiresAt());
        }
    }

    @ExceptionHandler(LoginFailedException.class)
    public ProblemDetail handleLoginFailed(LoginFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 429, not 401: the lockout is deliberately distinguishable (issue
    // #145 decision) so the UI can show the lockout message. Retry-After
    // repeats the same wait the message is phrased from, in the header
    // the standard defines for it
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }
}
