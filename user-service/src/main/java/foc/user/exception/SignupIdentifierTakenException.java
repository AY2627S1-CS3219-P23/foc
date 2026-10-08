/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5), date: 2026-09-30
Scope: PR #150 review (@Sinnez1): verify's answer when the email or
       username a pending sign-up reserved was taken by someone else
       while the code was in flight. A ResponseStatusException subclass,
       not a new shape — AuthController's existing passthrough handler
       maps it to the same 400 problem+json with the same reason — so
       that AuthService.verifySignup can name it in noRollbackFor and
       commit the one thing it must: discarding a pending sign-up that
       can never complete, which would otherwise hold its email until
       the code expired (a 10 minute lockout for a user who simply lost
       a username race).
Author review: Leong Wei Zhi to review via the PR.
2026-09-30 (Claude Code, Fable 5), issue #92: also thrown by the email-
change verify (the same taken-while-pending discard); the problem body
now carries a type URI, set here so every handler — AuthController's
local passthrough and the shared advice's — renders it for free.
*/

package foc.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SignupIdentifierTakenException extends ResponseStatusException {

    /**
     * Wraps the uniqueness refusal caught from
     * {@code NewAccountDetails}, keeping its reason and its problem
     * type (email-taken or username-taken — set there, the one place
     * that knows which check failed).
     */
    public SignupIdentifierTakenException(ResponseStatusException taken) {
        super(HttpStatus.BAD_REQUEST, taken.getReason());
        getBody().setType(taken.getBody().getType());
    }
}
