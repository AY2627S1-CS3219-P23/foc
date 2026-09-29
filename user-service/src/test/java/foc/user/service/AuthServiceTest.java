/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: unit tests for AuthService (issues #87/#89): sign-up normalisation
       and uniqueness, and login lookup, lockout, counter reset,
       non-revealing failures and recovery within the retention window.
       Fixed clock and a plain-text password encoder keep them fast.
       PR #141 review: username lookups ignore case (team decision).
       2026-09-29, Claude Code (Opus 5), issue #146: the non-revealing
       assertion is replaced by one per cause, and the login failures now
       pin the author's wording — the attempts countdown and the lockout's
       remaining minutes. PR #148 Copilot review: cases for part-second
       rounding and for an account past the retention window answering as
       gone whatever password (or lock) it carries.
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.LoginRequest;
import foc.user.dto.LoginResponse;
import foc.user.dto.SignupRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.AccountLockedException;
import foc.user.exception.LoginFailedException;
import foc.user.repository.UserRepository;
import foc.user.security.JwtIssuer;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final String PASSWORD = "ValidPassword123";

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

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtIssuer issuer = new JwtIssuer("test-jwt-secret-that-is-at-least-32-bytes-long", Duration.ofHours(1));
        authService = new AuthService(userRepository, PLAIN_ENCODER, issuer, 30,
            Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User("e1234567@u.nus.edu", "student_alex", PLAIN_ENCODER.encode(PASSWORD), Role.USER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    private void userFoundByUsername() {
        when(userRepository.findByUsernameIgnoreCase("student_alex")).thenReturn(Optional.of(user));
    }

    private LoginResponse login(String password) {
        return authService.login(new LoginRequest("student_alex", password));
    }

    // ---- sign-up ----

    @Test
    @DisplayName("Sign-up stores a normalised USER with a hashed password")
    void signup_createsUser() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.signup(
            new SignupRequest(" E1234567@U.NUS.EDU ", " student_alex ", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
        assertThat(saved.getValue().getUsername()).isEqualTo("student_alex");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Sign-up rejects a taken email with 400 and the exact reason")
    void signup_emailTaken() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        assertBadRequest(thrown, "Email is already registered");
        verify(userRepository, never()).saveAndFlush(any());
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
    @DisplayName("Sign-up losing a race on the unique columns is a 400, not a 500")
    void signup_concurrentDuplicate() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Throwable thrown = catchThrowable(() -> authService.signup(
            new SignupRequest("e1234567@u.nus.edu", "student_alex", PASSWORD)));

        assertBadRequest(thrown, "Email or username was just taken; choose another");
    }

    private static void assertBadRequest(Throwable thrown, String reason) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) thrown;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo(reason);
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
