/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: unit tests for issue #94's PasswordResetService, in
       AccountUpdateServiceTest's shape (Mockito, fixed Clock, a real
       OtpService over the hash-revealing PLAIN_ENCODER). The request
       cases prove the one observable answer: unknown email,
       past-window account and inside-the-cooldown repeat all return
       silently with no save and no mail; the stored hash is pinned as
       the encoder's hash of the exact code the email carries. The
       confirm cases prove the single failure answer (wrong code,
       unknown email, expired, spent — one exception, one wording, the
       dummy-hash cost for rows that don't exist), attempt counting
       with the row kept as spent, single use, the lockout clear, and
       that deletedAt survives a reset (owner decisions via options
       Q&A). Rewritten from the link-token version the same day, when
       the owner revisited the credential to an OTP code; types track
       V7's shape (PasswordResetOtp / password_reset_otps, no kind —
       owner decisions via options Q&A).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.PasswordResetConfirmRequest;
import foc.user.entity.PasswordResetOtp;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.ResetCodeInvalidException;
import foc.user.repository.PasswordResetOtpRepository;
import foc.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final long USER_ID = 42L;
    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int RETENTION_DAYS = 30;

    // stores "hashed:<raw>", so tests know every code's hash up front
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

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOtpRepository resetOtps;

    @Mock
    private OtpEmailSender otpEmailSender;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        OtpService otpService =
            new OtpService(PLAIN_ENCODER, OTP_TTL, OTP_MAX_ATTEMPTS, OTP_RESEND_COOLDOWN);
        service = new PasswordResetService(userRepository, resetOtps, PLAIN_ENCODER,
            otpService, otpEmailSender, RETENTION_DAYS, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User(EMAIL, "student_alex", PLAIN_ENCODER.encode("OldPassword123"), Role.USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private void accountFound() {
        // the locked-by-email finder is the flows' one user read
        // (PR #179 Copilot review: lock-first, so the state is fresh)
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private String emailedCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendPasswordResetCode(eq(EMAIL), code.capture(), eq(OTP_TTL));
        return code.getValue();
    }

    // request

    @Test
    @DisplayName("A request stores the hash of the exact 6-digit code the email carries")
    void requestReset_storesHashOfEmailedCode() {
        accountFound();
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        ArgumentCaptor<PasswordResetOtp> saved = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(resetOtps).save(saved.capture());
        String code = emailedCode();
        assertThat(code).matches("\\d{6}");
        assertThat(saved.getValue().getCodeHash()).isEqualTo(PLAIN_ENCODER.encode(code));
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        assertThat(saved.getValue().getAttempts()).isZero();
        assertThat(saved.getValue().getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("An unknown email is absorbed silently: no row, no mail")
    void requestReset_unknownEmailSilent() {
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        verify(resetOtps, never()).save(any());
        verify(otpEmailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("A deleted account inside the window still gets its reset code")
    void requestReset_deletedInWindowSends() {
        user.softDelete(NOW.minus(10, ChronoUnit.DAYS));
        accountFound();
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        verify(resetOtps).save(any());
        verify(otpEmailSender).sendPasswordResetCode(eq(EMAIL), anyString(), eq(OTP_TTL));
    }

    @Test
    @DisplayName("An account past the retention window is absorbed silently, like unknown")
    void requestReset_pastWindowSilent() {
        user.softDelete(NOW.minus(31, ChronoUnit.DAYS));
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestReset(EMAIL);

        verify(resetOtps, never()).save(any());
        verify(otpEmailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("A repeat inside the cooldown is absorbed silently — a 429 would reveal the account")
    void requestReset_insideCooldownSilent() {
        accountFound();
        PasswordResetOtp existing = new PasswordResetOtp(user, "hashed:111111",
            NOW.minusSeconds(30), NOW.minusSeconds(30).plus(OTP_TTL));
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.of(existing));

        service.requestReset(EMAIL);

        assertThat(existing.getCodeHash()).isEqualTo("hashed:111111");
        verify(resetOtps, never()).save(any());
        verify(otpEmailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("A repeat past the cooldown renews the one row: fresh code, life and attempt budget")
    void requestReset_pastCooldownRenews() {
        accountFound();
        PasswordResetOtp existing = new PasswordResetOtp(user, "hashed:111111",
            NOW.minusSeconds(61), NOW.minusSeconds(61).plus(OTP_TTL));
        existing.incrementAttempts();
        existing.incrementAttempts();
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.of(existing));

        service.requestReset(EMAIL);

        verify(resetOtps).save(existing);
        assertThat(existing.getCodeHash()).isEqualTo(PLAIN_ENCODER.encode(emailedCode()));
        assertThat(existing.getCreatedAt()).isEqualTo(NOW);
        assertThat(existing.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        // a fresh budget, unlike a sign-up resend: nothing is held
        // hostage here, so the cooldown alone caps renewal speed
        assertThat(existing.getAttempts()).isZero();
    }

    @Test
    @DisplayName("A mail failure is a 502 — the rollback leaves no code nobody received")
    void requestReset_mailFailureIs502() {
        accountFound();
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.empty());
        doThrow(new MailSendException("smtp down"))
            .when(otpEmailSender).sendPasswordResetCode(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.requestReset(EMAIL))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    // confirm

    private PasswordResetOtp storedCode(String code, Instant createdAt, Instant expiresAt) {
        PasswordResetOtp token = new PasswordResetOtp(user, PLAIN_ENCODER.encode(code),
            createdAt, expiresAt);
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.of(token));
        return token;
    }

    private static PasswordResetConfirmRequest confirm(String code) {
        return new PasswordResetConfirmRequest(EMAIL, code, "NewPassword123", "NewPassword123");
    }

    @Test
    @DisplayName("Confirm writes the new password, clears the lockout, and consumes the code")
    void confirmReset_success() {
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(NOW.plusSeconds(600));
        PasswordResetOtp token = storedCode("042042", NOW.minusSeconds(60), NOW.plusSeconds(540));

        service.confirmReset(confirm("042042"));

        assertThat(user.getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode("NewPassword123"));
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
        verify(resetOtps).delete(token);
    }

    @Test
    @DisplayName("Confirm on a deleted account resets the password but leaves it deleted")
    void confirmReset_deletedStaysDeleted() {
        Instant deletedAt = NOW.minus(10, ChronoUnit.DAYS);
        user.softDelete(deletedAt);
        storedCode("042042", NOW.minusSeconds(60), NOW.plusSeconds(540));

        service.confirmReset(confirm("042042"));

        // recovery stays an explicit, separate choice (login + restore)
        assertThat(user.getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode("NewPassword123"));
        assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("An unknown email answers exactly like a wrong code")
    void confirmReset_unknownEmail() {
        // also the purged-mid-flight shape (PR #179 review): the locked
        // read finding nothing answers exactly like unknown, dummy
        // BCrypt cost included
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class)
            .hasMessage("Reset code is invalid or has expired; request a new one");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An account with no code requested answers the same way")
    void confirmReset_noCodeRequested() {
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(resetOtps.findWithLockByUserId(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A wrong code answers the same way and counts an attempt")
    void confirmReset_wrongCodeCounts() {
        PasswordResetOtp token = storedCode("042042", NOW.minusSeconds(60), NOW.plusSeconds(540));

        assertThatThrownBy(() -> service.confirmReset(confirm("000000")))
            .isInstanceOf(ResetCodeInvalidException.class);

        assertThat(token.getAttempts()).isEqualTo(1);
        verify(resetOtps).save(token);
        verify(resetOtps, never()).delete(any(PasswordResetOtp.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("The exhausting guess gets the same answer — no 429 to reveal the account")
    void confirmReset_exhaustingGuessSameAnswer() {
        PasswordResetOtp token = storedCode("042042", NOW.minusSeconds(60), NOW.plusSeconds(540));
        for (int i = 0; i < OTP_MAX_ATTEMPTS - 1; i++) {
            token.incrementAttempts();
        }

        assertThatThrownBy(() -> service.confirmReset(confirm("000000")))
            .isInstanceOf(ResetCodeInvalidException.class)
            .hasMessage("Reset code is invalid or has expired; request a new one");

        // kept as spent, not deleted: createdAt still gates the next
        // code behind the cooldown (sign-up's PR #157 lesson)
        assertThat(token.getAttempts()).isEqualTo(OTP_MAX_ATTEMPTS);
        verify(resetOtps).save(token);
        verify(resetOtps, never()).delete(any(PasswordResetOtp.class));
    }

    @Test
    @DisplayName("A spent row refuses even the right code, with the same answer")
    void confirmReset_spentRefusesRightCode() {
        PasswordResetOtp token = storedCode("042042", NOW.minusSeconds(60), NOW.plusSeconds(540));
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            token.incrementAttempts();
        }

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class);
        assertThat(user.getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode("OldPassword123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("An expired code answers the same way, and its row goes")
    void confirmReset_expiredCode() {
        PasswordResetOtp token = storedCode("042042", NOW.minus(OTP_TTL).minusSeconds(60), NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class);
        verify(resetOtps).delete(token);
        verify(userRepository, never()).save(any());
        assertThat(user.getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode("OldPassword123"));
    }

    @Test
    @DisplayName("A code expiring exactly now is expired")
    void confirmReset_boundaryIsExpired() {
        storedCode("042042", NOW.minus(OTP_TTL), NOW);

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class);
    }

    @Test
    @DisplayName("An account past the window answers like unknown, before any row is touched")
    void confirmReset_pastWindowAnswersInvalid() {
        user.softDelete(NOW.minus(31, ChronoUnit.DAYS));
        when(userRepository.findWithLockByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirmReset(confirm("042042")))
            .isInstanceOf(ResetCodeInvalidException.class);
        verify(resetOtps, never()).findWithLockByUserId(any());
        verify(userRepository, never()).save(any());
    }
}
