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
       PR #148 Copilot review: the seconds are rounded up before the
       minutes are, so a fraction of a second can't be dropped twice and
       quote a wait shorter than the lock.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(Duration retryAfter) {
        this(seconds(retryAfter));
    }

    private AccountLockedException(long retryAfterSeconds) {
        super("Your account is locked due to too many failed login attempts. Try again in "
            + minutes(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    // what Retry-After reports, and what the message is phrased from, so
    // the header and the sentence can't disagree
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    // rounded up: part of a second is still a second to wait, and a lock
    // with milliseconds left still asks for a pause rather than none
    private static long seconds(Duration retryAfter) {
        return Math.max(1, retryAfter.plusNanos(999_999_999L).getSeconds());
    }

    // whole minutes, rounded up from the rounded-up seconds
    private static String minutes(long retryAfterSeconds) {
        long minutes = (retryAfterSeconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
