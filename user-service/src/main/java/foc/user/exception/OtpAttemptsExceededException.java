/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: attempts-exhausted signal for issue #88's POST /auth/signup/verify,
       mapped to 429 problem+json by AuthController (the status the
       login lockout uses, issue #145 precedent). Unlike the lockout
       there is no wait to quote: the pending sign-up is discarded and
       the remedy is a fresh sign-up.
Reviewed by: Leong Wei Zhi (via pull request).
2026-09-30 (Claude Code, Fable 5), issue #92: message constructor added —
the account-update flows' remedy is requesting a new code, not signing up.
2026-10-05 (Claude Code, Fable 5), PR #157 Copilot review: now that the
exhausted row stays behind as spent, there IS a wait to quote — the
resend cooldown gating its replacement — so this carries it for
Retry-After, OtpResendTooSoonException's pattern (0 = retry now).
*/

package foc.user.exception;

import java.time.Duration;

public class OtpAttemptsExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpAttemptsExceededException(Duration retryAfter) {
        this("Too many incorrect codes; sign up again to get a new code", retryAfter);
    }

    public OtpAttemptsExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfterSeconds = seconds(retryAfter);
    }

    /**
     * What Retry-After reports: the seconds until the resend cooldown
     * lets a replacement code be requested — 0 means right away. Part of
     * a second still counts as one (the cooldown exception's rounding).
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static long seconds(Duration retryAfter) {
        return Math.max(0, retryAfter.plusNanos(999_999_999L).getSeconds());
    }
}
