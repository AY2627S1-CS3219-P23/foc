/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: sign-up (issue #87) and login (issue #89), replacing PR #139's
       separate user-auth server (team decision: auth lives in
       user-service). Rules from design doc §3: username-or-email login,
       non-revealing failures, 15 min lockout after 5 consecutive failures,
       counters cleared on success; sign-up checks NUS email, password
       policy and uniqueness with exact 400 reasons. Team decisions: OTP is
       deferred, so sign-up inserts the account directly (the design's
       insert-after-verify shape, with verify skipped for now); a
       soft-deleted account is recovered by logging in within the
       retention window.
       PR #141 review: sign-up's normalising and uniqueness checks shared
       with owner setup (NewAccountDetails); usernames match ignoring case
       at sign-up and login (team decision).
       Issue #147: signup and login are no longer @Transactional, so
       bcrypt runs without holding a pooled connection (team decision);
       the attempt's counter update moved to LoginAttempts, which locks
       the row so parallel wrong guesses each count toward the lockout.
       2026-09-29, Claude Code (Fable 5), PR #142: lockout now raises
       AccountLockedException (a distinct message/status) instead of the
       generic failure — issue #145 decided by the author: the wireframe's
       lockout box wins over fully non-revealing failures, accepting that
       a locked account is thereby revealed to exist.
       2026-09-29, Claude Code (Opus 5), issue #146: each login failure
       now raises its own message (unknown account / wrong password with
       the attempts left / locked with the time left), the author's call.
       The timing-equalisation hash that made an unknown account answer as
       slowly as a wrong password went with it: the messages now say which
       happened, so equal timing hid nothing.
       PR #148 Copilot review: the retention-window check moved ahead of
       the lock and password checks, so an account past the window answers
       as gone whatever password is typed, instead of only when the
       password happened to be right.
       Merged with issue #147's LoginAttempts (2026-09-29, Claude Code,
       Opus 5.5): #146's messages now come from the outcome LoginAttempts
       records under the row lock (attempts left, time left, gone).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final LoginAttempts loginAttempts;
    private final Clock clock;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            LoginAttempts loginAttempts) {
        this(userRepository, passwordEncoder, jwtIssuer, loginAttempts, Clock.systemUTC());
    }

    AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            LoginAttempts loginAttempts,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.loginAttempts = loginAttempts;
        this.clock = clock;
    }

    // creates a USER account. OTP verification will sit before the insert
    // once it lands (POST /auth/signup/verify); until then sign-up inserts.
    // Not @Transactional: bcrypt runs without holding a pooled connection,
    // and the unique indexes catch a sign-up that races past the checks
    public UserResponse signup(SignupRequest request) {
        String email = NewAccountDetails.normalizeEmail(request.email());
        String username = NewAccountDetails.normalizeUsername(request.username());
        NewAccountDetails.ensureUnique(userRepository, email, username);

        User user = new User(email, username, passwordEncoder.encode(request.password()), Role.USER);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // a concurrent sign-up took the email or username after the checks
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Email or username was just taken; choose another");
        }
    }

    // Not @Transactional: the lookup and the bcrypt check run without
    // holding a pooled connection; LoginAttempts then records the attempt
    // in its own short transaction
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = findAccount(request.usernameOrEmail());
        if (found.isEmpty()) {
            throw LoginFailedException.unknownAccount();
        }

        User user = found.get();
        Instant now = clock.instant();

        // past the recovery window the account is waiting for the purge, so
        // it answers as if it were already gone — before the lock and the
        // password, so every attempt on it gets that one answer and none of
        // them count against a row that is on its way out
        if (loginAttempts.pastRetention(user, now)) {
            throw LoginFailedException.unknownAccount();
        }

        // locked: refused without counting the attempt, and told how long
        // is left rather than the full lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }

        // bcrypt, outside any transaction; LoginAttempts then counts the
        // attempt with the row locked, re-checking the lock a parallel
        // attempt may have set meanwhile
        boolean matched = passwordEncoder.matches(request.password(), user.getPasswordHash());
        LoginAttempts.Result result = loginAttempts.record(user.getId(), matched, now);
        return switch (result.outcome()) {
            case SUCCESS -> LoginResponse.bearer(jwtIssuer.issue(result.user()), jwtIssuer.ttl().toSeconds());
            // the attempt that trips the lock reports the lockout too, so
            // the user learns immediately rather than on the next try
            case LOCKED -> throw new AccountLockedException(result.retryAfter());
            case FAILED -> throw LoginFailedException.wrongPassword(result.attemptsLeft());
            // purged since the lookup
            case GONE -> throw LoginFailedException.unknownAccount();
        };
    }

    // usernames can't contain '@', so an '@' means an email
    private Optional<User> findAccount(String usernameOrEmail) {
        String identifier = usernameOrEmail.trim();
        return identifier.contains("@")
            ? userRepository.findByEmail(identifier.toLowerCase())
            : userRepository.findByUsernameIgnoreCase(identifier);
    }
}
