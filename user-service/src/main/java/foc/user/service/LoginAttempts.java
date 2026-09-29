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
Author review: Ryan to review via the PR.
*/

package foc.user.service;

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

    public enum Outcome { SUCCESS, FAILED, LOCKED }

    // the account as it stands after the attempt; null unless SUCCESS
    public record Result(Outcome outcome, User user) {
        static Result of(Outcome outcome) {
            return new Result(outcome, null);
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
            return Result.of(Outcome.FAILED);
        }
        try {
            // lock the row (SELECT ... FOR UPDATE) and re-read it: find() may
            // return a copy the request loaded earlier (open-in-view), which
            // would miss a parallel attempt's count
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException e) {
            return Result.of(Outcome.FAILED);
        }

        // locked by a parallel attempt since the lookup: not counted
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return Result.of(Outcome.LOCKED);
        }

        if (!passwordMatched) {
            // the attempt that trips the lock reports the lockout too, so
            // the user learns immediately rather than on the next try
            return Result.of(recordFailure(user, now) ? Outcome.LOCKED : Outcome.FAILED);
        }

        // past the recovery window the account is waiting for the purge
        if (!user.isActive()
                && user.getDeletedAt().isBefore(now.minus(retentionDays, ChronoUnit.DAYS))) {
            return Result.of(Outcome.FAILED);
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
        return new Result(Outcome.SUCCESS, user);
    }

    // returns whether this failure started the lockout
    private boolean recordFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        boolean locks = failures >= AuthService.MAX_FAILED_ATTEMPTS;
        if (locks) {
            // start the lockout and a fresh count for after it ends
            user.setLockedUntil(now.plus(AuthService.LOCKOUT));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(failures);
        }
        userRepository.save(user);
        return locks;
    }
}
