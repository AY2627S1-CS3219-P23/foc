/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: SMTP OtpEmailSender for issue #88, resolving the design doc's
       open provider comparison: Gmail SMTP via spring-boot-starter-mail,
       with the compose Mailpit container as the unauthenticated local
       default (chosen by Leong Wei Zhi via options Q&A). One
       JavaMailSender code path either way — .env decides the target.
       Failures propagate as MailException; AuthService turns them into
       a 502 and rolls the pending sign-up back.
       PR #150 Copilot review: the lifetime wording keeps sub-minute
       TTLs honest (seconds when not whole minutes) instead of
       truncating 30s to "0 minutes".
       2026-09-30, Claude Code (Opus 5), PR #150 re-review: now that a
       resend quotes the time actually left rather than a full TTL, a
       part-minute remainder is rounded to minutes instead of printed as
       raw seconds — running the flow showed a resent code advertised as
       "534 seconds" while the sign-up dialog called the same code
       "9 minutes".
Reviewed by: Leong Wei Zhi (via pull request).
2026-09-30 (Claude Code, Fable 5), issue #92: the account-update gate
and email-change confirmation messages, same shape as sign-up's; the
new-address message says what to do if the change wasn't yours.
*/

package foc.user.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpOtpEmailSender implements OtpEmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpOtpEmailSender(JavaMailSender mailSender, @Value("${user.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendSignupCode(String toEmail, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Your FoC verification code");
        message.setText("Your FoC sign-up verification code is " + code + ".\n\n"
            + "It expires in " + describe(validity) + ".\n\n"
            + "If you didn't sign up for FoC, you can ignore this email.");
        mailSender.send(message);
    }

    @Override
    public void sendAccountUpdateCode(String toEmail, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Your FoC account update code");
        message.setText("Your FoC account update code is " + code + ".\n\n"
            + "Enter it to confirm the change you requested to your account.\n\n"
            + "It expires in " + describe(validity) + ".\n\n"
            + "If you didn't request a change to your FoC account, you can "
            + "ignore this email; without this code nothing changes.");
        mailSender.send(message);
    }

    @Override
    public void sendEmailChangeCode(String toEmail, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Confirm your new FoC email");
        message.setText("Your FoC email confirmation code is " + code + ".\n\n"
            + "Enter it to make this address your FoC account email.\n\n"
            + "It expires in " + describe(validity) + ".\n\n"
            + "If you didn't ask to use this address for a FoC account, you "
            + "can ignore this email; the change won't happen.");
        mailSender.send(message);
    }

    // Minutes read naturally and are what the sign-up screen shows, so
    // anything from a minute up is quoted in minutes, rounded to the
    // nearest — a resent code with 8m54s left is "9 minutes", not "534
    // seconds". Below a minute it stays in seconds, so a sub-minute TTL
    // never truncates to "0 minutes" (PR #150 Copilot review).
    private static String describe(Duration validity) {
        long seconds = validity.toSeconds();
        if (seconds < 60) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }
        long minutes = Math.round(seconds / 60.0);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
