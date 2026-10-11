/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: confirm-failure signal for issue #94's
       POST /auth/password-reset/confirm, mapped to 400 problem+json by
       ProblemDetailAdvice. One message for a wrong code, an unknown
       email, an expired or spent code — any distinction would let an
       outsider probe which addresses hold accounts, and the remedy
       (request a fresh code) is the same either way. Shape follows
       OtpVerificationException; replaced ResetTokenInvalidException
       when the reset credential became an OTP code (owner decision
       via options Q&A).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

public class ResetCodeInvalidException extends RuntimeException {

    public ResetCodeInvalidException() {
        super("Reset code is invalid or has expired; request a new one");
    }
}
