/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: unit test for issue #88's SmtpOtpEmailSender: the composed
       message's addressing and that the body carries the code and its
       lifetime. The JavaMailSender is mocked — real SMTP is exercised
       manually against the compose Mailpit container.
       PR #150 Copilot review: a sub-minute TTL case pins the seconds
       wording (30s must not read as "0 minutes").
       PR #150 re-review: a part-minute remainder (what a resend leaves)
       is quoted in whole minutes, as the sign-up dialog does.
       2026-10-01, Claude Code (Opus 5), PR #157 Copilot review: the two
       #92 messages get their own cases. The account-update integration
       tests mock this interface, so nothing there would notice either
       method mailing the wrong address or dropping its code — the
       email-change code reaching the NEW address especially.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpOtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("The sign-up email carries the code, its lifetime and the configured from")
    void sendSignupCode() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@foc.local");

        sender.sendSignupCode("e1234567@u.nus.edu", "042042", Duration.ofMinutes(10));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@foc.local");
        assertThat(message.getValue().getTo()).containsExactly("e1234567@u.nus.edu");
        assertThat(message.getValue().getSubject()).isEqualTo("Your FoC verification code");
        assertThat(message.getValue().getText())
            .contains("042042")
            .contains("10 minutes");
    }

    @Test
    @DisplayName("A part-minute remainder reads in minutes, like the sign-up dialog")
    void sendSignupCode_partMinuteLifetime() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@foc.local");

        // what a resend leaves: 8m54s of the original 10 minutes
        sender.sendSignupCode("e1234567@u.nus.edu", "042042", Duration.ofSeconds(534));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText())
            .contains("9 minutes")
            .doesNotContain("534");
    }

    @Test
    @DisplayName("The account-update code goes to the address given, with its code and lifetime")
    void sendAccountUpdateCode() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@foc.local");

        // the gate code's recipient is the account's CURRENT email (F2.1.1):
        // the caller passes it, so this pins that the sender doesn't
        // substitute anything else
        sender.sendAccountUpdateCode("e1234567@u.nus.edu", "042042", Duration.ofMinutes(10));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@foc.local");
        assertThat(message.getValue().getTo()).containsExactly("e1234567@u.nus.edu");
        assertThat(message.getValue().getSubject()).isEqualTo("Your FoC account update code");
        assertThat(message.getValue().getText())
            .contains("042042")
            .contains("10 minutes");
    }

    @Test
    @DisplayName("The email-change code goes to the NEW address, with its code and lifetime")
    void sendEmailChangeCode() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@foc.local");

        // F2.1.3: this one confirms the new address, so it must never go to
        // the account's current email — the recipient is the whole point
        sender.sendEmailChangeCode("e7654321@u.nus.edu", "042042", Duration.ofMinutes(10));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@foc.local");
        assertThat(message.getValue().getTo()).containsExactly("e7654321@u.nus.edu");
        assertThat(message.getValue().getSubject()).isEqualTo("Confirm your new FoC email");
        assertThat(message.getValue().getText())
            .contains("042042")
            .contains("10 minutes");
    }

    @Test
    @DisplayName("A sub-minute lifetime is quoted in seconds, never truncated to 0 minutes")
    void sendSignupCode_subMinuteLifetime() {
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@foc.local");

        sender.sendSignupCode("e1234567@u.nus.edu", "042042", Duration.ofSeconds(30));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText())
            .contains("30 seconds")
            .doesNotContain("0 minutes");
    }
}
