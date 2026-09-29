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
Reviewed by: Leong Wei Zhi (via pull request).
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
            + "It expires in " + validity.toMinutes() + " minutes.\n\n"
            + "If you didn't sign up for FoC, you can ignore this email.");
        mailSender.send(message);
    }
}
