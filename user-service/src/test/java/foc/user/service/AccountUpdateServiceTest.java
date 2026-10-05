/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: unit tests for issue #92's AccountUpdateService, the clock-exact
       cases the integration suite can't pin (AuthServiceTest's harness:
       mocked repositories, a fixed Clock, and the PLAIN_ENCODER so a
       real OtpService runs with predictable hashes): cooldown boundary
       math and Retry-After seconds, a resend answering with the time
       actually LEFT rather than a fresh TTL, expired-row takeover
       resetting attempts, the gate code's consume/count/limit ladder,
       and expiry's !isAfter(now) boundary (a code expiring exactly now
       is expired).
       PR #157 Copilot review: the two post-flush collision fallbacks are
       pinned to their problem+json types, so a client can identify a
       taken name or address in the racing case as well as the checked one.
Author review: Leong Wei Zhi to review via the PR.
2026-10-05 (Claude Code, Fable 5), PR #157 review (@Sinnez1): exhaustion
now keeps an OTP row as spent instead of deleting it, so the resend
cooldown still gates the next code — exhaustion/takeover tests updated
and cooldown-bypass regressions added.
Same day, PR #157 Copilot re-review: the harness stubs the locked
active-user lookup the flows now take first.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.controller.ProblemTypes;
import foc.user.dto.UpdateAccountRequest;
import foc.user.entity.AccountUpdateOtp;
import foc.user.entity.PendingEmailChange;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountUpdateServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static final long USER_ID = 42L;
    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final String NEW_EMAIL = "e7654321@u.nus.edu";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);

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
    private AccountUpdateOtpRepository gateOtps;

    @Mock
    private PendingEmailChangeRepository pendingEmailChanges;

    @Mock
    private OtpEmailSender otpEmailSender;

    private AccountUpdateService service;
    private User user;

    @BeforeEach
    void setUp() {
        OtpService otpService =
            new OtpService(PLAIN_ENCODER, OTP_TTL, OTP_MAX_ATTEMPTS, OTP_RESEND_COOLDOWN);
        service = new AccountUpdateService(userRepository, gateOtps, pendingEmailChanges,
            PLAIN_ENCODER, otpService, otpEmailSender, Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User(EMAIL, "student_alex", PLAIN_ENCODER.encode("ValidPassword123"), Role.USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        // the locked variant: every flow takes the user-row lock first, so
        // the address a gate code is mailed to cannot go stale between the
        // read and the send (PR #157 Copilot review)
        when(userRepository.getActiveUserWithLock(USER_ID)).thenReturn(user);
    }

    // a live gate row whose current code was sent secondsAgo
    private AccountUpdateOtp gateRow(long sentSecondsAgo) {
        AccountUpdateOtp gate = new AccountUpdateOtp(USER_ID, PLAIN_ENCODER.encode("111111"),
            NOW.minusSeconds(sentSecondsAgo), NOW.minusSeconds(sentSecondsAgo).plus(OTP_TTL));
        when(gateOtps.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(gate));
        return gate;
    }

    // ---- requestOtp: cooldown boundaries and resend timings ----

    @Test
    @DisplayName("A resend exactly at the cooldown boundary is allowed")
    void requestOtp_exactlyAtCooldownAllowed() {
        AccountUpdateOtp gate = gateRow(OTP_RESEND_COOLDOWN.getSeconds());
        when(gateOtps.saveAndFlush(gate)).thenReturn(gate);

        service.requestOtp(USER_ID);

        verify(otpEmailSender).sendAccountUpdateCode(eq(EMAIL), anyString(), any());
    }

    @Test
    @DisplayName("One second inside the cooldown is 429, and Retry-After quotes that second")
    void requestOtp_insideCooldownRefused() {
        gateRow(OTP_RESEND_COOLDOWN.getSeconds() - 1);

        OtpResendTooSoonException refusal = catchThrowableOfType(
            OtpResendTooSoonException.class, () -> service.requestOtp(USER_ID));

        assertThat(refusal.retryAfterSeconds()).isEqualTo(1);
        verify(otpEmailSender, never()).sendAccountUpdateCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("A resend answers with the time the code has LEFT, not a fresh TTL")
    void requestOtp_resendQuotesRemainingValidity() {
        AccountUpdateOtp gate = gateRow(90);
        when(gateOtps.saveAndFlush(gate)).thenReturn(gate);

        var response = service.requestOtp(USER_ID);

        // sent 90s ago with a 600s TTL: 510s left, and the expiry is untouched
        assertThat(response.expiresInSeconds()).isEqualTo(510);
        assertThat(gate.getExpiresAt()).isEqualTo(NOW.minusSeconds(90).plus(OTP_TTL));
    }

    @Test
    @DisplayName("An expired gate row is taken over fresh: new expiry, attempts reset")
    void requestOtp_expiredRowReplaced() {
        AccountUpdateOtp gate = new AccountUpdateOtp(USER_ID, PLAIN_ENCODER.encode("111111"),
            NOW.minus(OTP_TTL).minusSeconds(120), NOW.minusSeconds(120));
        gate.incrementAttempts();
        when(gateOtps.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(gate));
        when(gateOtps.saveAndFlush(gate)).thenReturn(gate);

        var response = service.requestOtp(USER_ID);

        assertThat(response.expiresInSeconds()).isEqualTo(OTP_TTL.getSeconds());
        assertThat(gate.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        assertThat(gate.getAttempts()).isZero();
    }

    // ---- the gate code's consume/count/limit ladder ----

    private UpdateAccountRequest rename(String otp) {
        return new UpdateAccountRequest("renamed_alex", null, otp);
    }

    @Test
    @DisplayName("A matching gate code is consumed: the row is deleted with the change")
    void updateAccount_consumesGate() {
        AccountUpdateOtp gate = gateRow(0);
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("renamed_alex", USER_ID))
            .thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        var result = service.updateAccount(USER_ID, rename("111111"));

        assertThat(result.user().username()).isEqualTo("renamed_alex");
        assertThat(result.emailParked()).isFalse();
        verify(gateOtps).delete(gate);
    }

    @Test
    @DisplayName("A wrong code counts an attempt and saves it; the change never runs")
    void updateAccount_wrongCodeCounts() {
        AccountUpdateOtp gate = gateRow(0);

        assertThatThrownBy(() -> service.updateAccount(USER_ID, rename("222222")))
            .isInstanceOf(OtpVerificationException.class)
            .hasMessage("Invalid verification code");

        assertThat(gate.getAttempts()).isEqualTo(1);
        verify(gateOtps).save(gate);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("The wrong guess that reaches the limit keeps the row as spent, not deleted")
    void updateAccount_limitKeepsGateSpent() {
        AccountUpdateOtp gate = gateRow(0);
        for (int i = 0; i < OTP_MAX_ATTEMPTS - 1; i++) {
            gate.incrementAttempts();
        }

        OtpAttemptsExceededException refusal = catchThrowableOfType(
            OtpAttemptsExceededException.class,
            () -> service.updateAccount(USER_ID, rename("222222")));

        // the 429 quotes the cooldown left before a replacement code may
        // be requested — the code was just sent, so the full 60s
        // (PR #157 Copilot review)
        assertThat(refusal.retryAfterSeconds()).isEqualTo(OTP_RESEND_COOLDOWN.getSeconds());
        // kept so its lastSentAt still holds the next request behind the
        // cooldown: deleting it here let 5 wrong guesses buy an immediate
        // fresh code (PR #157 review, @Sinnez1)
        assertThat(gate.getAttempts()).isEqualTo(OTP_MAX_ATTEMPTS);
        verify(gateOtps).save(gate);
        verify(gateOtps, never()).delete(any(AccountUpdateOtp.class));
    }

    @Test
    @DisplayName("A spent gate refuses even the right code, without counting or deleting")
    void updateAccount_spentGateRefusesRightCode() {
        AccountUpdateOtp gate = gateRow(0);
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            gate.incrementAttempts();
        }

        assertThatThrownBy(() -> service.updateAccount(USER_ID, rename("111111")))
            .isInstanceOf(OtpAttemptsExceededException.class)
            .hasMessage("Too many incorrect codes; request a new code");

        assertThat(gate.getAttempts()).isEqualTo(OTP_MAX_ATTEMPTS);
        verify(gateOtps, never()).delete(any(AccountUpdateOtp.class));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Exhausting the attempts does not get around the resend cooldown (PR #157 review)")
    void requestOtp_spentRowStillCoolsDown() {
        AccountUpdateOtp gate = gateRow(OTP_RESEND_COOLDOWN.getSeconds() - 1);
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            gate.incrementAttempts();
        }

        OtpResendTooSoonException refusal = catchThrowableOfType(
            OtpResendTooSoonException.class, () -> service.requestOtp(USER_ID));

        assertThat(refusal.retryAfterSeconds()).isEqualTo(1);
        verify(otpEmailSender, never()).sendAccountUpdateCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Once the cooldown passes, a spent gate row is replaced fresh: new TTL, attempts reset")
    void requestOtp_spentRowReplacedAfterCooldown() {
        AccountUpdateOtp gate = gateRow(OTP_RESEND_COOLDOWN.getSeconds());
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            gate.incrementAttempts();
        }
        when(gateOtps.saveAndFlush(gate)).thenReturn(gate);

        var response = service.requestOtp(USER_ID);

        assertThat(response.expiresInSeconds()).isEqualTo(OTP_TTL.getSeconds());
        assertThat(gate.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        assertThat(gate.getAttempts()).isZero();
    }

    @Test
    @DisplayName("A gate code expiring exactly now is expired, and the dead row is swept")
    void updateAccount_expiryBoundary() {
        AccountUpdateOtp gate = new AccountUpdateOtp(USER_ID, PLAIN_ENCODER.encode("111111"),
            NOW.minus(OTP_TTL), NOW);
        when(gateOtps.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(gate));

        assertThatThrownBy(() -> service.updateAccount(USER_ID, rename("111111")))
            .isInstanceOf(OtpVerificationException.class)
            .hasMessage("Code has expired; request a new code");

        verify(gateOtps).delete(gate);
    }

    // ---- pending email change ----

    @Test
    @DisplayName("Resending a pending change quotes the seconds left and keeps the expiry")
    void resendEmailChange_quotesRemainingValidity() {
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW.minusSeconds(90), NOW.minusSeconds(90).plus(OTP_TTL));
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));
        when(pendingEmailChanges.save(pending)).thenReturn(pending);

        var response = service.resendEmailChange(USER_ID);

        assertThat(response.expiresInSeconds()).isEqualTo(510);
        assertThat(response.email()).isEqualTo(NEW_EMAIL);
        assertThat(pending.getExpiresAt()).isEqualTo(NOW.minusSeconds(90).plus(OTP_TTL));
        verify(otpEmailSender).sendEmailChangeCode(eq(NEW_EMAIL), anyString(), any());
    }

    @Test
    @DisplayName("Replacing a pending change with a different address resets its attempts")
    void updateAccount_replaceResetsAttempts() {
        AccountUpdateOtp gate = gateRow(0);
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW.minus(OTP_RESEND_COOLDOWN), NOW.plusSeconds(300));
        pending.incrementAttempts();
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));
        when(pendingEmailChanges.saveAndFlush(pending)).thenReturn(pending);
        when(userRepository.existsByEmailAndIdNot("e1111111@u.nus.edu", USER_ID)).thenReturn(false);

        var result = service.updateAccount(USER_ID,
            new UpdateAccountRequest(null, "e1111111@u.nus.edu", "111111"));

        assertThat(result.emailParked()).isTrue();
        assertThat(pending.getNewEmail()).isEqualTo("e1111111@u.nus.edu");
        assertThat(pending.getAttempts()).isZero();
        // a genuinely new change gets a fresh TTL
        assertThat(pending.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        assertThat(result.expiresInSeconds()).isEqualTo(OTP_TTL.getSeconds());
        verify(gateOtps).delete(gate);
    }

    @Test
    @DisplayName("Repeating the change on a spent pending row replaces it fresh instead of renewing")
    void updateAccount_spentPendingChangeReplaced() {
        gateRow(0);
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW.minus(OTP_RESEND_COOLDOWN), NOW.plusSeconds(300));
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            pending.incrementAttempts();
        }
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));
        when(pendingEmailChanges.saveAndFlush(pending)).thenReturn(pending);
        when(userRepository.existsByEmailAndIdNot(NEW_EMAIL, USER_ID)).thenReturn(false);

        var result = service.updateAccount(USER_ID,
            new UpdateAccountRequest(null, NEW_EMAIL, "111111"));

        // renewing would hand out a code no attempt budget could match;
        // a spent row is dead like an expired one, so the same address
        // gets a genuinely new change (PR #157 review, @Sinnez1)
        assertThat(result.emailParked()).isTrue();
        assertThat(pending.getAttempts()).isZero();
        assertThat(pending.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
    }

    @Test
    @DisplayName("A spent pending change cannot resend its dead code")
    void resendEmailChange_spentRefused() {
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW.minus(OTP_RESEND_COOLDOWN), NOW.plusSeconds(300));
        for (int i = 0; i < OTP_MAX_ATTEMPTS; i++) {
            pending.incrementAttempts();
        }
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.resendEmailChange(USER_ID))
            .isInstanceOf(OtpAttemptsExceededException.class)
            .hasMessage("Too many incorrect codes; request the email change again for a new code");

        verify(otpEmailSender, never()).sendEmailChangeCode(anyString(), anyString(), any());
        verify(pendingEmailChanges, never()).delete(any(PendingEmailChange.class));
    }

    // ---- the post-flush collision fallbacks carry their documented types ----

    @Test
    @DisplayName("A username lost to a race is still typed username-taken")
    void updateAccount_usernameRaceIsTyped() {
        gateRow(0);
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("renamed_alex", USER_ID))
            .thenReturn(false);
        // the pre-check passed and the unique index refused the write: the
        // client must still be able to tell what collided (PR #157 review)
        when(userRepository.saveAndFlush(user))
            .thenThrow(new DataIntegrityViolationException("uq_users_username"));

        ResponseStatusException refusal = catchThrowableOfType(
            ResponseStatusException.class, () -> service.updateAccount(USER_ID, rename("111111")));

        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal.getBody().getType()).isEqualTo(ProblemTypes.USERNAME_TAKEN);
    }

    @Test
    @DisplayName("A username race in a combined username+email PATCH is typed too, not a 500")
    void updateAccount_combinedPatchUsernameRaceIsTyped() {
        gateRow(0);
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("renamed_alex", USER_ID))
            .thenReturn(false);
        when(userRepository.saveAndFlush(user))
            .thenThrow(new DataIntegrityViolationException("uq_users_username"));

        // before the fix the email branch's uniqueness query ran first and
        // Hibernate's auto-flush wrote the dirty username there, so the
        // unique-index violation escaped this catch as a 500
        // (PR #157 review, @Sinnez1)
        ResponseStatusException refusal = catchThrowableOfType(ResponseStatusException.class,
            () -> service.updateAccount(USER_ID,
                new UpdateAccountRequest("renamed_alex", NEW_EMAIL, "111111")));

        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal.getBody().getType()).isEqualTo(ProblemTypes.USERNAME_TAKEN);
        // the username flushed through its typed catch before anything
        // parked: no email change started, no code went out
        verify(pendingEmailChanges, never()).findWithLockByUserId(USER_ID);
        verify(otpEmailSender, never()).sendEmailChangeCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("An email lost to a race at verify is still typed email-taken")
    void verifyEmailChange_emailRaceIsTyped() {
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW, NOW.plus(OTP_TTL));
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));
        when(userRepository.existsByEmailAndIdNot(NEW_EMAIL, USER_ID)).thenReturn(false);
        when(userRepository.saveAndFlush(user))
            .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        ResponseStatusException refusal = catchThrowableOfType(ResponseStatusException.class,
            () -> service.verifyEmailChange(USER_ID, "333333"));

        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal.getBody().getType()).isEqualTo(ProblemTypes.EMAIL_TAKEN);
        // the row survives the rollback; the next verify's re-check discards it
        verify(pendingEmailChanges, never()).delete(pending);
    }

    @Test
    @DisplayName("Changing email nullifies OTPs sent to the old email")
    void verifyEmailChangeClearsGate() {
        PendingEmailChange pending = new PendingEmailChange(USER_ID, NEW_EMAIL,
            PLAIN_ENCODER.encode("333333"), NOW, NOW.plus(OTP_TTL));
        when(pendingEmailChanges.findWithLockByUserId(USER_ID)).thenReturn(Optional.of(pending));
        when(userRepository.existsByEmailAndIdNot(NEW_EMAIL, USER_ID)).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        
        AccountUpdateOtp gate = gateRow(1);
        service.verifyEmailChange(USER_ID, "333333");
        
        verify(pendingEmailChanges).delete(pending);
        verify(gateOtps).delete(gate);
    }
}
