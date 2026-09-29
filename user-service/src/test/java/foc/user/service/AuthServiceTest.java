/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: unit tests for AuthService (issues #87/#89): sign-up normalisation
       and uniqueness, and login lookup, lockout, counter reset,
       non-revealing failures and recovery within the retention window.
       Fixed clock and a plain-text password encoder keep them fast.
       PR #141 review: username lookups ignore case (team decision).
       Issue #147: built with the LoginAttempts the counters moved to.
Author review: Ryan to review via the PR.
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
import static org.mockito.Mockito.lenient;
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
import jakarta.persistence.EntityManager;

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

    @Mock
    private EntityManager entityManager;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtIssuer issuer = new JwtIssuer("test-jwt-secret-that-is-at-least-32-bytes-long", Duration.ofHours(1));
        authService = new AuthService(userRepository, PLAIN_ENCODER, issuer,
            new LoginAttempts(userRepository, entityManager, 30), Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User("e1234567@u.nus.edu", "student_alex", PLAIN_ENCODER.encode(PASSWORD), Role.USER);
        ReflectionTestUtils.setField(user, "id", 42L);
        // LoginAttempts re-reads the account with the row locked (refresh is a no-op here)
        lenient().when(entityManager.find(User.class, 42L)).thenReturn(user);
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
    @DisplayName("Unknown account and wrong password fail with the same message")
    void login_failuresAreNonRevealing() {
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
        userFoundByUsername();

        Throwable unknown = catchThrowable(() -> authService.login(new LoginRequest("nobody", PASSWORD)));
        Throwable wrongPassword = catchThrowable(() -> login("WrongPassword123"));

        assertThat(unknown).isInstanceOf(LoginFailedException.class);
        assertThat(wrongPassword).isInstanceOf(LoginFailedException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("A wrong password counts one failed attempt")
    void login_wrongPasswordCounts() {
        userFoundByUsername();

        assertThatThrownBy(() -> login("WrongPassword123")).isInstanceOf(LoginFailedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("The fifth consecutive failure locks the account for 15 minutes")
    void login_fifthFailureLocks() {
        userFoundByUsername();
        user.setFailedLoginAttempts(4);

        // the tripping attempt already reports the lockout (issue #145)
        assertThatThrownBy(() -> login("WrongPassword123")).isInstanceOf(AccountLockedException.class);

        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("A locked account is refused even with the right password, without counting")
    void login_lockedRefused() {
        userFoundByUsername();
        user.setLockedUntil(NOW.plusSeconds(60));

        assertThatThrownBy(() -> login(PASSWORD)).isInstanceOf(AccountLockedException.class);

        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).save(any());
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

        assertThatThrownBy(() -> login(PASSWORD)).isInstanceOf(LoginFailedException.class);
        assertThat(user.isActive()).isFalse();
    }
}
