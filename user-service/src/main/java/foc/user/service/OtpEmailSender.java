/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: outbound-email port for issue #88. An interface so AuthService
       and its tests depend on "an OTP email goes out", not on SMTP;
       the email-change (#92) and password-reset (#94) flows add their
       own methods here.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.time.Duration;

/** Sends the platform's account emails (SMTP impl: {@link SmtpOtpEmailSender}). */
public interface OtpEmailSender {

    /**
     * Emails a sign-up verification code. Implementations must never
     * log the code.
     *
     * @param validity how long the code stays valid, quoted in the email
     */
    void sendSignupCode(String toEmail, String code, Duration validity);
}
