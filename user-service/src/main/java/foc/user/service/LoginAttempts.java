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
       2026-10-05 (issue #154): the row is read and locked in one query.
       2026-10-09, PR #162 review (Leong Wei Zhi): back to find + refresh (author's
       choice), so the read is fresh even if the User is already
       loaded in the persistence context.
Author review: Ryan to review via the PR.
2026-10-11 (Claude Code, Fable 5), issue #94: login no longer silently
recovers a soft-deleted account (owner decision via options Q&A,
reversing the #93-era team decision): it succeeds and AuthService
reports the scheduled purge date (purgeAt) so the SPA can offer the
explicit choice — restore via POST /users/me/recover, or keep deleted.
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
            // lock the row (SELECT ... FOR UPDATE) and re-read it: find()
            // returns the copy already in the persistence context if there
            // is one (open-in-view, or a caller's transaction), which would
            // miss a parallel attempt's count. One extra query per attempt
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
        // row's @Version and could make a concurrent admin action fail.
        // deletedAt deliberately survives a login (issue #94): recovery
        // is an explicit choice now, made at POST /users/me/recover
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
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

    // when the purge will take a soft-deleted account (day 31's sweep
    // condition, deletedAt older than the retention window), or null for
    // an active one. Lives here with the retention knob it shares with
    // pastRetention; AuthService quotes it in the LoginResponse so the
    // SPA can offer the restore-or-keep-deleted choice (issue #94)
    public Instant purgeAt(User user) {
        return user.isActive()
            ? null
            : user.getDeletedAt().plus(retentionDays, ChronoUnit.DAYS);
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
