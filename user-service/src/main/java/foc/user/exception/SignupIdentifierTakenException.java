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
*/

package foc.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SignupIdentifierTakenException extends ResponseStatusException {

    public SignupIdentifierTakenException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
