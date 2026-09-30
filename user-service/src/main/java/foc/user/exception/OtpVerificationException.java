/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: verify-failure signal for issue #88's POST /auth/signup/verify,
       mapped to 400 problem+json by AuthController. The default
       message covers a wrong code and an unknown email alike — with no
       pending row there is nothing to count an attempt against, so the
       two are deliberately indistinguishable; expiry gets its own
       wording because its remedy (sign up again) is different.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.exception;

public class OtpVerificationException extends RuntimeException {

    public OtpVerificationException() {
        this("Invalid verification code");
    }

    public OtpVerificationException(String message) {
        super(message);
    }
}
