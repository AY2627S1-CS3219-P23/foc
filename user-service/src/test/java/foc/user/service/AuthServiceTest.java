/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: unit tests for AuthService (issues #87/#89): sign-up normalisation
       and uniqueness, and login lookup, lockout, counter reset,
       non-revealing failures and recovery within the retention window.
       Fixed clock and a plain-text password encoder keep them fast.
       PR #141 review: username lookups ignore case (team decision).
       Issue #147: built with the LoginAttempts the counters moved to.
       2026-09-29, Claude Code (Opus 5), issue #146: the non-revealing
       assertion is replaced by one per cause, and the login failures now
       pin the author's wording — the attempts countdown and the lockout's
       remaining minutes. PR #148 Copilot review: cases for part-second
       rounding and for an account past the retention window answering as
       gone whatever password (or lock) it carries.
       2026-09-29, Claude Code (Fable 5), issue #88: the sign-up cases now
       assert the pending-row-and-email shape (no users insert, renewal on
       repeat, mail failure to 502), and a verify section covers the code
       match, attempt counting, expiry, exhaustion and the verify-time
       uniqueness re-check. A real OtpService (plain encoder) keeps the
       code round-trip honest; only the email sender is mocked.
       PR #150 Copilot review: verify stubs moved to the locked finder,
       and the expiry case pins the boundary (a code expiring exactly
       now is expired).
       PR #150 author review: the repeat-sign-up cases now pin who may
       move a live pending row along — its own details resend (keeping the
       attempt count), anything else is a 409 rather than a silent merge,
       and an expired row is taken over outright — plus the resend
       cooldown's 429 and the Retry-After it quotes.
       2026-09-30, Claude Code (Opus 5), PR #150 re-review: a resend keeps
       the row's original expiry (and the email quotes the seconds left),
       and a verify whose email or username was taken meanwhile discards
       the row instead of leaving it to block the address.
       PR #150 Copilot review: sign-up's pending-row read is stubbed on the
       locked finder, which is what it now uses.
       2026-10-05, Claude Code (Opus 5.5), issue #154: the stub follows
       LoginAttempts' single locked read.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.LoginRequest;
import foc.user.dto.LoginResponse;
import foc.user.dto.SignupRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.AccountLockedException;
import foc.user.exception.LoginFailedException;
import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.exception.SignupIdentifierTakenException;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.security.JwtIssuer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final String PASSWORD = "ValidPassword123";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);

    // stores "hashed:<raw>", so tests can build users with a known password
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
    private PendingSignupRepository pendingSignupRepository;

    @Mock
    private OtpEmailSender otpEmailSender;

    @Mock
    private EntityManager entityManager;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtIssuer issuer = new JwtIssuer("test-jwt-secret-that-is-at-least-32-bytes-long", Duration.ofHours(1));
        OtpService otpService =
            new OtpService(PLAIN_ENCODER, OTP_TTL, OTP_MAX_ATTEMPTS, OTP_RESEND_COOLDOWN);
        authService = new AuthService(userRepository, pendingSignupRepository, PLAIN_ENCODER,
            issuer, otpService, otpEmailSender,
            new LoginAttempts(userRepository, entityManager, 30), Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User("e1234567@u.nus.edu", "student_alex", PLAIN_ENCODER.encode(PASSWORD), Role.USER);
        ReflectionTestUtils.setField(user, "id", 42L);
        // LoginAttempts re-reads the account with the row locked
        lenient().when(entityManager.find(User.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
    }

    private void userFoundByUsername() {
        when(userRepository.findByUsernameIgnoreCase("student_alex")).thenReturn(Optional.of(user));
    }

    private LoginResponse login(String password) {
        return authService.login(new LoginRequest("student_alex", password));
    }

    // ---- sign-up ----

    @Test
    @DisplayName("Sign-up parks a normalised pending row and emails the code — no users insert")
    void signup_parksPendingAndEmailsCode() {
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SignupResponse response = authService.signup(
            new SignupRequest(" E1234567@U.NUS.EDU ", " student_alex ", PASSWORD));

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL));

        assertThat(saved.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
        assertThat(saved.getValue().getUsername()).isEqualTo("student_alex");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        // the emailed code is 6 digits and only its hash is stored
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getCodeHash()).isEqualTo("hashed:" + code.getValue());
        assertThat(saved.getValue().getLastSentAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        assertThat(response.email()).isEqualTo("e1234567@u.nus.edu");
        assertThat(response.expiresInSeconds()).isEqualTo(OTP_TTL.toSeconds());
        // the cooldown the OTP dialog disables its resend button for
        assertThat(response.resendInSeconds()).isEqualTo(OTP_RESEND_COOLDOWN.toSeconds());
        verify(userRepository, never()).saveAndFlush(any());
    }

    // a live pending sign-up for the usual email, pended at `sentAt`
    private PendingSignup existingPending(String username, String password, Instant sentAt) {
        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", username,
            PLAIN_ENCODER.encode(password), "hashed:000000", sentAt, sentAt.plus(OTP_TTL));
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        return existing;
    }

    private SignupResponse signupAgain(String username, String password) {
        return authService.signup(new SignupRequest("e1234567@u.nus.edu", username, password));
    }

    @Test
    @DisplayName("A repeat of the same request resends: fresh code, same details, attempts kept")
    void signup_repeatOfSameRequestResends() {
        // the cooldown has passed, so this one is allowed to send
        PendingSignup existing = existingPending("student_alex", PASSWORD, NOW.minusSeconds(90));
        existing.incrementAttempts();
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // the username may come back in different case: sign-up matches
        // usernames ignoring case, and so does this
        SignupResponse response = signupAgain("Student_Alex", PASSWORD);

        verify(pendingSignupRepository).saveAndFlush(existing);
        // read under the row lock, so two repeats for one email queue
        // instead of both passing the cooldown and overwriting each
        // other's code (PR #150 Copilot review)
        verify(pendingSignupRepository).findWithLockByEmail("e1234567@u.nus.edu");
        verify(pendingSignupRepository, never()).findByEmail(any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        // pended 90s ago, so 90s of the TTL is gone: the email and the 202
        // quote what is left, not a fresh TTL
        Duration left = OTP_TTL.minusSeconds(90);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(left));
        assertThat(response.expiresInSeconds()).isEqualTo(left.toSeconds());
        assertThat(existing.getCodeHash()).isEqualTo("hashed:" + code.getValue());
        assertThat(existing.getLastSentAt()).isEqualTo(NOW);
        // the expiry the first request set, untouched: a resend that moved
        // it could hold the address for ever (PR #150 re-review)
        assertThat(existing.getExpiresAt()).isEqualTo(NOW.minusSeconds(90).plus(OTP_TTL));
        // the details the first request supplied, untouched — and the
        // attempt it already spent, so resending can't reset the limit
        assertThat(existing.getUsername()).isEqualTo("student_alex");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(existing.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("A resend late in the window hands out the seconds that are left, not a fresh TTL")
    void signup_lateResendKeepsShortValidity() {
        // pended with 30s left: no resend may stretch that back to 10 min,
        // so the row always reaches its "expired" branch
        PendingSignup existing = existingPending(
            "student_alex", PASSWORD, NOW.minus(OTP_TTL).plusSeconds(30));
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SignupResponse response = signupAgain("student_alex", PASSWORD);

        verify(otpEmailSender).sendSignupCode(
            eq("e1234567@u.nus.edu"), any(), eq(Duration.ofSeconds(30)));
        assertThat(response.expiresInSeconds()).isEqualTo(30);
        assertThat(existing.getExpiresAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    @DisplayName("A repeat with other details is a 409: a live pending sign-up is never rewritten")
    void signup_repeatWithOtherDetailsRefused() {
        // the hijack this refusal exists for: whoever receives the code
        // must not be able to verify it into an account whose username
        // and password someone else supplied
        PendingSignup existing = existingPending("student_alex", PASSWORD, NOW.minusSeconds(90));

        Throwable thrown = catchThrowable(() -> signupAgain("attacker_x", "AttackerPass123"));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) thrown;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).isEqualTo(AuthService.SIGNUP_IN_PROGRESS);
        assertThat(existing.getUsername()).isEqualTo("student_alex");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(existing.getCodeHash()).isEqualTo("hashed:000000");
        verify(pendingSignupRepository, never()).saveAndFlush(any());
        verify(otpEmailSender, never()).sendSignupCode(any(), any(), any());
    }

    @Test
    @DisplayName("A repeat with the right details inside the cooldown is a 429 quoting the wait")
    void signup_resendInsideCooldown() {
        existingPending("student_alex", PASSWORD, NOW.minusSeconds(20));

        Throwable thrown = catchThrowable(() -> signupAgain("student_alex", PASSWORD));

        assertThat(thrown).isInstanceOf(OtpResendTooSoonException.class);
        // 60s cooldown, 20s gone: 40 left, in the header and the sentence
        assertThat(((OtpResendTooSoonException) thrown).retryAfterSeconds()).isEqualTo(40);
        assertThat(thrown).hasMessage(
            "A verification code was sent to this email moments ago. Try again in 40 seconds.");
        verify(otpEmailSender, never()).sendSignupCode(any(), any(), any());
    }

    @Test
    @DisplayName("An expired pending sign-up is dead: a new sign-up takes its row over outright")
    void signup_expiredPendingTakenOver() {
        // expired an hour ago, so it guards nothing — including against
        // details that would be a 409 while it was live
        PendingSignup existing = existingPending("old_name", "OldPassword1", NOW.minus(Duration.ofHours(1)));
        existing.incrementAttempts();
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        signupAgain("student_alex", PASSWORD);

        verify(pendingSignupRepository).saveAndFlush(existing);
        assertThat(existing.getUsername()).isEqualTo("student_alex");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(existing.getLastSentAt()).isEqualTo(NOW);
        assertThat(existing.getExpiresAt()).isEqualTo(NOW.plus(OTP_TTL));
        // a fresh sign-up, so a fresh attempt budget
        assertThat(existing.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Sign-up rejects a taken email with 400 and the exact reason")
    void signup_emailTaken() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        assertBadRequest(thrown, "Email is already registered");
        verify(pendingSignupRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Sign-up rejects a taken username with 400 and the exact reason")
    void signup_usernameTaken() {
        when(userRepository.existsByUsernameIgnoreCase("student_alex")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        assertBadRequest(thrown, "Username is already taken");
    }

    @Test
    @DisplayName("Two first sign-ups for one email at once: the loser is a 409, not a 500")
    void signup_concurrentDuplicate() {
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        // the same answer as finding the row up front: the situation is
        // identical, the race only made it arrive half a millisecond later
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) thrown;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).isEqualTo(AuthService.SIGNUP_IN_PROGRESS);
        verify(otpEmailSender, never()).sendSignupCode(any(), any(), any());
    }

    @Test
    @DisplayName("A mail failure is a 502, so the pending row rolls back with it")
    void signup_mailFailure() {
        when(pendingSignupRepository.saveAndFlush(any(PendingSignup.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("connection refused"))
            .when(otpEmailSender).sendSignupCode(any(), any(), any());

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) thrown;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(e.getReason()).isEqualTo("Could not send the verification email; try again later");
    }

    private static void assertBadRequest(Throwable thrown, String reason) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) thrown;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo(reason);
    }

    // ---- sign-up verify ----

    private PendingSignup pendingWithCode(String code) {
        PendingSignup pending = new PendingSignup("e1234567@u.nus.edu", "student_alex",
            "hashed:" + PASSWORD, "hashed:" + code, NOW, NOW.plus(OTP_TTL));
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(pending));
        return pending;
    }

    private UserResponse verifyCode(String code) {
        return authService.verifySignup(new SignupVerifyRequest("e1234567@u.nus.edu", code));
    }

    @Test
    @DisplayName("The right code inserts the user with the stored hash and clears the pending row")
    void verify_createsUser() {
        PendingSignup pending = pendingWithCode("123456");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.verifySignup(
            new SignupVerifyRequest(" E1234567@U.NUS.EDU ", "123456"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
        assertThat(saved.getValue().getUsername()).isEqualTo("student_alex");
        // the hash from sign-up is carried over, never re-encoded
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(response.role()).isEqualTo("USER");
        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    @DisplayName("A wrong code counts an attempt and answers 400 without naming which part failed")
    void verify_wrongCodeCounts() {
        PendingSignup pending = pendingWithCode("123456");

        assertThatThrownBy(() -> verifyCode("654321"))
            .isInstanceOf(OtpVerificationException.class)
            .hasMessage("Invalid verification code");

        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(pendingSignupRepository).save(pending);
        verify(pendingSignupRepository, never()).delete(any(PendingSignup.class));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("An email with no pending sign-up answers exactly like a wrong code")
    void verify_unknownEmail() {
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyCode("123456"))
            .isInstanceOf(OtpVerificationException.class)
            .hasMessage("Invalid verification code");
    }

    @Test
    @DisplayName("A code expiring exactly now is already expired and discards the pending sign-up")
    void verify_expired() {
        // the boundary case: expires_at == now must reject, not accept
        PendingSignup pending = new PendingSignup("e1234567@u.nus.edu", "student_alex",
            "hashed:" + PASSWORD, "hashed:123456", NOW.minus(OTP_TTL), NOW);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> verifyCode("123456"))
            .isInstanceOf(OtpVerificationException.class)
            .hasMessage("Code has expired; sign up again to get a new code");

        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    @DisplayName("The wrong code that exhausts the attempts discards the pending sign-up")
    void verify_attemptsExhausted() {
        PendingSignup pending = pendingWithCode("123456");
        for (int i = 0; i < OTP_MAX_ATTEMPTS - 1; i++) {
            pending.incrementAttempts();
        }

        assertThatThrownBy(() -> verifyCode("654321"))
            .isInstanceOf(OtpAttemptsExceededException.class)
            .hasMessage("Too many incorrect codes; sign up again to get a new code");

        verify(pendingSignupRepository).delete(pending);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("An identifier taken since sign-up fails the verify and discards the pending row")
    void verify_identifierTakenSinceSignup() {
        PendingSignup pending = pendingWithCode("123456");
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> verifyCode("123456"));

        assertBadRequest(thrown, "Email is already registered");
        assertThat(thrown).isInstanceOf(SignupIdentifierTakenException.class);
        verify(userRepository, never()).saveAndFlush(any());
        // the row can never complete now, so it must stop holding the
        // email: keeping it locked out whoever lost the race for the rest
        // of the TTL (PR #150 re-review)
        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    @DisplayName("A username taken since sign-up discards the pending row too")
    void verify_usernameTakenSinceSignup() {
        PendingSignup pending = pendingWithCode("123456");
        when(userRepository.existsByUsernameIgnoreCase("student_alex")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> verifyCode("123456"));

        assertBadRequest(thrown, "Username is already taken");
        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    @DisplayName("Verify losing the insert race on the unique columns is a 400, not a 500")
    void verify_concurrentDuplicate() {
        pendingWithCode("123456");
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Throwable thrown = catchThrowable(() -> verifyCode("123456"));

        assertBadRequest(thrown, "Email or username was just taken; choose another");
    }

    // ---- login ----

    @Test
    @DisplayName("Login by username returns a bearer token and clears the counters")
    void login_byUsername() {
        userFoundByUsername();
        user.setFailedLoginAttempts(3);

        LoginResponse response = login(PASSWORD);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("A login that changes nothing doesn't write the row")
    void login_cleanAccountNotSaved() {
        userFoundByUsername();

        assertThat(login(PASSWORD).accessToken()).isNotBlank();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login by email looks the account up by its normalised email")
    void login_byEmail() {
        when(userRepository.findByEmail("e1234567@u.nus.edu")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest(" E1234567@U.NUS.EDU ", PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        verify(userRepository, never()).findByUsernameIgnoreCase(any());
    }

    @Test
    @DisplayName("An unknown account says so instead of blaming the password")
    void login_unknownAccountNamed() {
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> authService.login(new LoginRequest("nobody", PASSWORD)));

        assertThat(thrown).isInstanceOf(LoginFailedException.class)
            .hasMessage("No account found for that username or email.");
    }

    @Test
    @DisplayName("A wrong password counts one failed attempt and says how many are left")
    void login_wrongPasswordCounts() {
        userFoundByUsername();

        assertThatThrownBy(() -> login("WrongPassword123"))
            .isInstanceOf(LoginFailedException.class)
            .hasMessage("Incorrect password. 4 attempts remaining"
                + " before your account is temporarily locked.");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("The last attempt before the lock is counted in the singular")
    void login_countdownReachesOneAttempt() {
        userFoundByUsername();
        user.setFailedLoginAttempts(3);

        assertThatThrownBy(() -> login("WrongPassword123"))
            .isInstanceOf(LoginFailedException.class)
            .hasMessage("Incorrect password. 1 attempt remaining"
                + " before your account is temporarily locked.");
    }

    @Test
    @DisplayName("The fifth consecutive failure locks the account for 15 minutes")
    void login_fifthFailureLocks() {
        userFoundByUsername();
        user.setFailedLoginAttempts(4);

        // the tripping attempt already reports the lockout (issue #145)
        Throwable thrown = catchThrowable(() -> login("WrongPassword123"));

        assertThat(thrown).isInstanceOf(AccountLockedException.class)
            .hasMessage("Your account is locked due to too many failed login attempts."
                + " Try again in 15 minutes.");
        assertThat(((AccountLockedException) thrown).retryAfterSeconds()).isEqualTo(900);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("A locked account is refused even with the right password, without counting")
    void login_lockedRefused() {
        userFoundByUsername();
        user.setLockedUntil(NOW.plusSeconds(60));

        Throwable thrown = catchThrowable(() -> login(PASSWORD));

        assertThat(thrown).isInstanceOf(AccountLockedException.class)
            .hasMessage("Your account is locked due to too many failed login attempts."
                + " Try again in 1 minute.");
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A locked account reports the time left, rounded up to whole minutes")
    void login_lockedReportsTimeLeft() {
        userFoundByUsername();
        user.setLockedUntil(NOW.plusSeconds(11 * 60 + 30));

        Throwable thrown = catchThrowable(() -> login(PASSWORD));

        assertThat(thrown).hasMessage("Your account is locked due to too many failed login"
            + " attempts. Try again in 12 minutes.");
        assertThat(((AccountLockedException) thrown).retryAfterSeconds()).isEqualTo(11 * 60 + 30);
    }

    @Test
    @DisplayName("A lock with seconds left still asks for a full minute")
    void login_lockedRoundsUpFromSeconds() {
        userFoundByUsername();
        user.setLockedUntil(NOW.plusSeconds(5));

        assertThatThrownBy(() -> login(PASSWORD))
            .hasMessage("Your account is locked due to too many failed login attempts."
                + " Try again in 1 minute.");
    }

    @Test
    @DisplayName("Part of a second left is rounded up before the minutes are")
    void login_lockedRoundsUpPartSeconds() {
        userFoundByUsername();
        // 60.5s: a wait quoted as 1 minute would run out before the lock does
        user.setLockedUntil(NOW.plusSeconds(60).plusMillis(500));

        Throwable thrown = catchThrowable(() -> login(PASSWORD));

        assertThat(thrown).hasMessage("Your account is locked due to too many failed login"
            + " attempts. Try again in 2 minutes.");
        assertThat(((AccountLockedException) thrown).retryAfterSeconds()).isEqualTo(61);
    }

    @Test
    @DisplayName("Once the lockout has passed, the right password logs in again")
    void login_afterLockout() {
        userFoundByUsername();
        user.setLockedUntil(NOW.minusSeconds(1));

        assertThat(login(PASSWORD).accessToken()).isNotBlank();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Logging in within the retention window recovers a soft-deleted account")
    void login_recoversDeletedAccount() {
        userFoundByUsername();
        user.softDelete(NOW.minus(Duration.ofDays(10)));

        assertThat(login(PASSWORD).accessToken()).isNotBlank();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("An account deleted longer ago than the retention window can't log in")
    void login_deletedPastWindowRefused() {
        userFoundByUsername();
        user.softDelete(NOW.minus(Duration.ofDays(31)));

        // past the window it is waiting for the purge, so it answers as gone
        assertThatThrownBy(() -> login(PASSWORD))
            .isInstanceOf(LoginFailedException.class)
            .hasMessage("No account found for that username or email.");
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("An account past the window answers as gone whatever password is typed")
    void login_deletedPastWindowIgnoresPassword() {
        userFoundByUsername();
        user.softDelete(NOW.minus(Duration.ofDays(31)));

        assertThatThrownBy(() -> login("WrongPassword123"))
            .isInstanceOf(LoginFailedException.class)
            .hasMessage("No account found for that username or email.");

        // nothing is counted against a row that is on its way out
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A lock on an account past the window doesn't outrank the purge")
    void login_deletedPastWindowBeatsLock() {
        userFoundByUsername();
        user.softDelete(NOW.minus(Duration.ofDays(31)));
        user.setLockedUntil(NOW.plusSeconds(600));

        assertThatThrownBy(() -> login(PASSWORD))
            .isInstanceOf(LoginFailedException.class)
            .hasMessage("No account found for that username or email.");
    }
}
