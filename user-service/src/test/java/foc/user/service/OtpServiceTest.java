/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: unit tests for issue #88's OtpService: code format, hash/match
       round-trip through the injected encoder, the config accessors,
       and the fail-loud guards on nonsense config.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class OtpServiceTest {

    private static final PasswordEncoder PLAIN_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encode(raw).equals(encoded);
        }
    };

    private final OtpService otpService =
        new OtpService(PLAIN_ENCODER, Duration.ofMinutes(10), 5);

    @Test
    @DisplayName("Codes are exactly six digits, zero-padded")
    void generateCode_sixDigits() {
        // a small code (e.g. 7) must come out as 000007, so sample a few
        for (int i = 0; i < 100; i++) {
            assertThat(otpService.generateCode()).matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("A code matches its own hash and only its own hash")
    void hashAndMatch() {
        String hash = otpService.hash("123456");

        assertThat(hash).isNotEqualTo("123456");
        assertThat(otpService.matches("123456", hash)).isTrue();
        assertThat(otpService.matches("654321", hash)).isFalse();
    }

    @Test
    @DisplayName("The configured ttl and attempt limit are what the accessors return")
    void configAccessors() {
        assertThat(otpService.ttl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(otpService.maxAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Nonsense config fails at construction, not on the first sign-up")
    void invalidConfigRejected() {
        assertThatThrownBy(() -> new OtpService(PLAIN_ENCODER, Duration.ZERO, 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OTP_TTL");
        assertThatThrownBy(() -> new OtpService(PLAIN_ENCODER, Duration.ofMinutes(10), 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OTP_MAX_ATTEMPTS");
    }
}
