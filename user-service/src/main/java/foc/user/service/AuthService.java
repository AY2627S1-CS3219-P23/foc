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
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final int retentionDays;
    private final Clock clock;
    // compared against when no account matches, so an unknown username or
    // email takes as long as a wrong password
    private final String unknownAccountHash;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            @Value("${user.retention.days}") int retentionDays) {
        this(userRepository, passwordEncoder, jwtIssuer, retentionDays, Clock.systemUTC());
    }

    AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            int retentionDays,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.retentionDays = retentionDays;
        this.clock = clock;
        this.unknownAccountHash = passwordEncoder.encode("no-such-account");
    }

    // creates a USER account. OTP verification will sit before the insert
    // once it lands (POST /auth/signup/verify); until then sign-up inserts
    @Transactional
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

    // noRollbackFor: a failed attempt must still commit its counter update
    @Transactional(noRollbackFor = {LoginFailedException.class, AccountLockedException.class})
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

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // the attempt that trips the lock reports the lockout too, so
            // the user learns immediately rather than on the next try
            if (recordFailure(user, now)) {
                throw new AccountLockedException();
            }
            throw new LoginFailedException();
        }

        // past the recovery window the account is waiting for the purge
        if (!user.isActive()
                && user.getDeletedAt().isBefore(now.minus(retentionDays, ChronoUnit.DAYS))) {
            throw new LoginFailedException();
        }

        // only write when something changes: an unconditional save bumps the
        // row's @Version and could make a concurrent admin action fail
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null || !user.isActive()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            // logging in within the window recovers a soft-deleted account
            user.setDeletedAt(null);
            userRepository.save(user);
        }

        return LoginResponse.bearer(jwtIssuer.issue(user), jwtIssuer.ttl().toSeconds());
    }

    // usernames can't contain '@', so an '@' means an email
    private Optional<User> findAccount(String usernameOrEmail) {
        String identifier = usernameOrEmail.trim();
        return identifier.contains("@")
            ? userRepository.findByEmail(identifier.toLowerCase())
            : userRepository.findByUsernameIgnoreCase(identifier);
    }

    // returns whether this failure started the lockout
    private boolean recordFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        boolean locks = failures >= MAX_FAILED_ATTEMPTS;
        if (locks) {
            // start the lockout and a fresh count for after it ends
            user.setLockedUntil(now.plus(LOCKOUT));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failures);
        }
        userRepository.save(user);
        return locks;
    }
}
