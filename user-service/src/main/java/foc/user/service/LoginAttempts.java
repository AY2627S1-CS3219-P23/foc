/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: issue #147. The database write for a login attempt, split out of
       AuthService.login so the bcrypt check runs outside any transaction
       (team decision: no pooled connection held while hashing), and so
       parallel attempts on one account each count: the row is locked
       while the counter is read and updated, so the lockout can no longer
       be skipped by sending guesses at once (the version-check race).
       Lockout, recovery and retention rules unchanged from AuthService.
       Merged with issue #146 (PR #148): the outcome carries the attempts
       left and the lock's remaining time for its messages, and the
       retention check runs before anything else (pastRetention).
Author review: Ryan to review via the PR.
*/

package foc.user.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import foc.user.entity.User;
import foc.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;

@Component
public class LoginAttempts {

    public enum Outcome { SUCCESS, FAILED, LOCKED, GONE }

    // user: the account after a SUCCESS; retryAfter: the lock's time left
    // (LOCKED); attemptsLeft: failures left before the lock (FAILED)
    public record Result(Outcome outcome, User user, Duration retryAfter, int attemptsLeft) {
        static Result success(User user) {
            return new Result(Outcome.SUCCESS, user, null, 0);
        }

        static Result failed(int attemptsLeft) {
            return new Result(Outcome.FAILED, null, null, attemptsLeft);
        }

        static Result locked(Duration retryAfter) {
            return new Result(Outcome.LOCKED, null, retryAfter, 0);
        }

        static Result gone() {
            return new Result(Outcome.GONE, null, null, 0);
        }
    }

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final int retentionDays;

    LoginAttempts(
            UserRepository userRepository,
            EntityManager entityManager,
            @Value("${user.retention.days}") int retentionDays) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.retentionDays = retentionDays;
    }

    // Records one checked attempt. Runs after the password check, with the
    // row locked from the read to the commit: parallel attempts queue here,
    // so each sees the previous one's count. Returns an outcome instead of
    // throwing, so a failure's counter update still commits. Public: Spring's
    // transaction proxy only wraps public methods, and without the
    // transaction the row lock ends with the read
    @Transactional
    public Result record(Long userId, boolean passwordMatched, Instant now) {
        User user = entityManager.find(User.class, userId);
        if (user == null) {
            // purged between the lookup and now
            return Result.gone();
        }
        try {
            // lock the row (SELECT ... FOR UPDATE) and re-read it: find() may
            // return a copy the request loaded earlier (open-in-view), which
            // would miss a parallel attempt's count
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException e) {
            return Result.gone();
        }

        // locked by a parallel attempt since the lookup: not counted
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return Result.locked(Duration.between(now, user.getLockedUntil()));
        }

        if (!passwordMatched) {
            int failures = recordFailure(user, now);
            return failures >= AuthService.MAX_FAILED_ATTEMPTS
                ? Result.locked(AuthService.LOCKOUT)
                : Result.failed(AuthService.MAX_FAILED_ATTEMPTS - failures);
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
        return Result.success(user);
    }

    // past the recovery window the account is waiting for the purge, so
    // login answers as if it were already gone, before any other check
    boolean pastRetention(User user, Instant now) {
        return !user.isActive()
            && user.getDeletedAt().isBefore(now.minus(retentionDays, ChronoUnit.DAYS));
    }

    // returns the running failure count this attempt made;
    // MAX_FAILED_ATTEMPTS means it just locked
    private int recordFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        if (failures >= AuthService.MAX_FAILED_ATTEMPTS) {
            // start the lockout and a fresh count for after it ends
            user.setLockedUntil(now.plus(AuthService.LOCKOUT));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failures);
        }
        userRepository.save(user);
        return failures;
    }
}
