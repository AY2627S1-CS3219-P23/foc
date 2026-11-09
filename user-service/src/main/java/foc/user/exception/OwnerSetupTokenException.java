/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-09
Scope: PR #309 review (@Sinnez1): the owner setup token's two refusals
       (503 unconfigured, 403 wrong) built in one place, with their
       problem types, instead of by hand at each call site. A
       ResponseStatusException subclass, like SignupIdentifierTakenException,
       so the existing passthrough handler renders it unchanged and
       AccountUpdateService.upgradeToOwner can name it in noRollbackFor:
       the gate code a wrong token spent must stay spent (code-first
       order, decided by Leong Wei Zhi via options Q&A, 2026-11-09).
       The 403 carries Retry-After, the wait until a new code may be
       requested, as OtpAttemptsExceededException's 429 does.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import foc.user.controller.ProblemTypes;

public class OwnerSetupTokenException extends ResponseStatusException {

    private final HttpHeaders headers = new HttpHeaders();

    private OwnerSetupTokenException(HttpStatus status, String reason, URI type) {
        super(status, reason);
        getBody().setType(type);
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    /** No owner setup token is configured on the server (503). */
    public static OwnerSetupTokenException missing() {
        return new OwnerSetupTokenException(HttpStatus.SERVICE_UNAVAILABLE,
            "Owner setup is unavailable", ProblemTypes.OWNER_SETUP_TOKEN_MISSING);
    }

    /**
     * The owner setup token sent to POST /users/me/owner is wrong or blank
     * (403, #308). The gate code it came with is spent, and Retry-After is
     * the wait until the next one may be requested (0 means right away).
     */
    public static OwnerSetupTokenException invalid(Duration retryAfter) {
        OwnerSetupTokenException e = new OwnerSetupTokenException(HttpStatus.FORBIDDEN,
            "Owner setup token is incorrect; request a new code to try again",
            ProblemTypes.OWNER_SETUP_TOKEN_INVALID);
        long seconds = Math.max(0, retryAfter.plusNanos(999_999_999L).getSeconds());
        e.headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        return e;
    }
}
