/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: outbound-email port for issue #88. An interface so AuthService
       and its tests depend on "an OTP email goes out", not on SMTP;
       the email-change (#92) and password-reset (#94) flows add their
       own methods here.
Reviewed by: Leong Wei Zhi (via pull request).
2026-09-30 (Claude Code, Fable 5), issue #92: the two account-update
methods this header promised — the gate code to the current email and
the confirmation code to the new one.
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

    /**
     * Emails an account-update gate code (#92, F2.1.1) to the account's
     * CURRENT address: possession of this inbox is what authorizes a
     * change. Never log the code.
     *
     * @param validity how long the code stays valid, quoted in the email
     */
    void sendAccountUpdateCode(String toEmail, String code, Duration validity);

    /**
     * Emails an email-change confirmation code (#92, F2.1.3) to the NEW
     * address: the change applies only once this code is verified. Never
     * log the code.
     *
     * @param validity how long the code stays valid, quoted in the email
     */
    void sendEmailChangeCode(String toEmail, String code, Duration validity);
}
