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
Author review: Ryan to review via the PR.
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
    // compared against when no account matches, so an unknown username or
    // email takes as long as a wrong password
    private final String unknownAccountHash;

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
        this.unknownAccountHash = passwordEncoder.encode("no-such-account");
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
            passwordEncoder.matches(request.password(), unknownAccountHash);
            throw new LoginFailedException();
        }

        User user = found.get();
        Instant now = clock.instant();

        // locked: refused without counting the attempt. The password is
        // still hashed so a locked account answers as slowly as any other
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            passwordEncoder.matches(request.password(), unknownAccountHash);
            throw new AccountLockedException();
        }

        boolean matched = passwordEncoder.matches(request.password(), user.getPasswordHash());
        LoginAttempts.Result result = loginAttempts.record(user.getId(), matched, now);
        return switch (result.outcome()) {
            case SUCCESS -> LoginResponse.bearer(jwtIssuer.issue(result.user()), jwtIssuer.ttl().toSeconds());
            case LOCKED -> throw new AccountLockedException();
            case FAILED -> throw new LoginFailedException();
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
