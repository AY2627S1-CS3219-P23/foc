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
2026-09-30 (Claude Code, Fable 5), issue #92: carries a problem+json type
URI (decision by Leong Wei Zhi via options Q&A: machine-readable types in
scope) plus expired()/required() factories, so the account-update flows'
distinct failures — expired code, no gate code, no pending email change —
keep one exception shape and one 400 handler.
*/

package foc.user.exception;

import java.net.URI;

import foc.user.controller.ProblemTypes;

public class OtpVerificationException extends RuntimeException {

    private final URI type;

    public OtpVerificationException() {
        this("Invalid verification code");
    }

    public OtpVerificationException(String message) {
        this(message, ProblemTypes.OTP_INVALID);
    }

    private OtpVerificationException(String message, URI type) {
        super(message);
        this.type = type;
    }

    /** The code (or the operation holding it) has expired. */
    public static OtpVerificationException expired(String message) {
        return new OtpVerificationException(message, ProblemTypes.OTP_EXPIRED);
    }

    /** No gate code exists; the caller must request one first (#92). */
    public static OtpVerificationException required(String message) {
        return new OtpVerificationException(message, ProblemTypes.OTP_REQUIRED);
    }

    /** No email change is pending for this account (#92). */
    public static OtpVerificationException noPendingEmailChange(String message) {
        return new OtpVerificationException(message, ProblemTypes.EMAIL_CHANGE_NONE);
    }

    /** The problem+json type URI the 400 handler attaches. */
    public URI type() {
        return type;
    }
}
