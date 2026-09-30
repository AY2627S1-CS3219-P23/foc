/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: attempts-exhausted signal for issue #88's POST /auth/signup/verify,
       mapped to 429 problem+json by AuthController (the status the
       login lockout uses, issue #145 precedent). Unlike the lockout
       there is no wait to quote: the pending sign-up is discarded and
       the remedy is a fresh sign-up.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.exception;

public class OtpAttemptsExceededException extends RuntimeException {

    public OtpAttemptsExceededException() {
        super("Too many incorrect codes; sign up again to get a new code");
    }
}
