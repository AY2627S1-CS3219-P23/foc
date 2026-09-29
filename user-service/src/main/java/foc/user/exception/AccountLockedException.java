/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: distinct lockout failure for login (PR #142, issue #145 decided
       by the author: the wireframe's lockout message wins over fully
       non-revealing failures). Raised when a login hits a locked
       account or when an attempt trips the lock; unlike
       LoginFailedException it deliberately reveals the lock state.
       2026-09-29, Claude Code (Opus 5), issue #146: the wait is now
       computed from the lockout's own end rather than a hard-coded
       "15 minutes" that could drift from AuthService.LOCKOUT, and is
       exposed for the Retry-After header (author's wording choice).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

    private final Duration retryAfter;

    public AccountLockedException(Duration retryAfter) {
        super("Your account is locked due to too many failed login attempts. Try again in "
            + wait(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    // whole minutes, rounded up and never zero: a lock with 30 s left is
    // still a minute the user has to wait
    private static String wait(Duration retryAfter) {
        long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
