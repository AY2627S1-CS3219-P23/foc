/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: unit test for issue #88's SmtpOtpEmailSender: the composed
       message's addressing and that the body carries the code and its
       lifetime. The JavaMailSender is mocked — real SMTP is exercised
       manually against the compose Mailpit container.
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
}
