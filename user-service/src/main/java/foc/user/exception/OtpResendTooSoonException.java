/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5), date: 2026-09-29
Scope: resend-cooldown refusal for issue #88's POST /auth/signup, added
       in PR #150 review (cooldown chosen by Leong Wei Zhi via options
       Q&A). Mapped to 429 problem+json with Retry-After by
       AuthController, like AccountLockedException — but the wait is
       seconds, not minutes, so the message quotes seconds.
       It does tell a caller that a code was sent to this email recently,
       which sign-up's exact 400 reasons ("Email is already registered")
       already do for the registered case; the alternative, answering 202
       without sending, would lie to a user asking for a resend.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

import java.time.Duration;

public class OtpResendTooSoonException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpResendTooSoonException(Duration retryAfter) {
        this(seconds(retryAfter));
    }

    private OtpResendTooSoonException(long retryAfterSeconds) {
        super("A verification code was sent to this email moments ago. Try again in "
            + (retryAfterSeconds == 1 ? "1 second" : retryAfterSeconds + " seconds") + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    // what Retry-After reports, and what the message is phrased from, so
    // the header and the sentence can't disagree (AccountLockedException's
    // pattern, and its rounding: part of a second is still a second)
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static long seconds(Duration retryAfter) {
        return Math.max(1, retryAfter.plusNanos(999_999_999L).getSeconds());
    }
}
