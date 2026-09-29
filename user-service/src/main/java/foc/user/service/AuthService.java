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
Author review: Leong Wei Zhi to review via the PR.
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
            throw LoginFailedException.unknownAccount();
        }

        User user = found.get();
        Instant now = clock.instant();

        // past the recovery window the account is waiting for the purge, so
        // it answers as if it were already gone — before the lock and the
        // password, so every attempt on it gets that one answer and none of
        // them count against a row that is on its way out
        if (!user.isActive()
                && user.getDeletedAt().isBefore(now.minus(retentionDays, ChronoUnit.DAYS))) {
            throw LoginFailedException.unknownAccount();
        }

        // locked: refused without counting the attempt, and told how long
        // is left rather than the full lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failures = recordFailure(user, now);
            // the attempt that trips the lock reports the lockout too, so
            // the user learns immediately rather than on the next try
            if (failures >= MAX_FAILED_ATTEMPTS) {
                throw new AccountLockedException(LOCKOUT);
            }
            throw LoginFailedException.wrongPassword(MAX_FAILED_ATTEMPTS - failures);
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

    // returns the running failure count this attempt made, so the caller
    // can say how many are left; MAX_FAILED_ATTEMPTS means it just locked
    private int recordFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        if (failures >= MAX_FAILED_ATTEMPTS) {
            // start the lockout and a fresh count for after it ends
            user.setLockedUntil(now.plus(LOCKOUT));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failures);
        }
        userRepository.save(user);
        return failures;
    }
}
