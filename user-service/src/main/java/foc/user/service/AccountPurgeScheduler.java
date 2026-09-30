/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-27
Scope: day-31 purge scheduler for issue #93 (design doc §2: "purge on
       day 31"), following notification-service's RetentionPurgeScheduler.
       FK cleanup as bulk deletes inside the purge transaction chosen by
       Leong Wei Zhi via options Q&A (over DB-level ON DELETE CASCADE);
       window and cadence are env-overridable like the notification purge.
       2026-09-29, Claude Code (Fable 5), issue #88: the otps sweep went
       with the otps table (sign-up OTPs live in pending_signups now); the
       same run sweeps expired pending sign-ups instead — hygiene only,
       since verify rejects an expired row on sight.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import foc.user.repository.AccountTokenRepository;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

/**
 * Hard-deletes soft-deleted accounts once their recovery window has
 * passed (design doc §2: soft delete = 30-day reuse block, purge on
 * day 31). Until this job runs, a deleted row keeps occupying its
 * unique email/username; the purge is what releases them. Dependent
 * account_tokens rows are removed first, in the same transaction, so
 * the users delete never trips their user_id FK. The run also sweeps
 * pending sign-ups whose code has expired (issue #88).
 */
@Component
class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final AccountTokenRepository accountTokens;
    private final PendingSignupRepository pendingSignups;
    private final UserRepository users;
    private final int retentionDays;

    AccountPurgeScheduler(AccountTokenRepository accountTokens,
            PendingSignupRepository pendingSignups,
            UserRepository users, @Value("${user.retention.days}") int retentionDays) {
        this.accountTokens = accountTokens;
        this.pendingSignups = pendingSignups;
        this.users = users;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${user.retention.purge-cron}")
    @Transactional
    void purgeExpiredDeletedAccounts() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        int tokenCount = accountTokens.deleteByUserDeletedBefore(cutoff);
        int userCount = users.deleteByDeletedBefore(cutoff);
        int pendingCount = pendingSignups.deleteByExpiresAtBefore(now);
        log.info("purged {} account(s) deleted before {} (with {} account token(s))"
                + " and {} expired pending sign-up(s)",
                userCount, cutoff, tokenCount, pendingCount);
    }
}
