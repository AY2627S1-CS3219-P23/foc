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
2026-09-30 (Claude Code, Fable 5), issue #92: the run also sweeps expired
account-update gate codes and pending email changes — before the users
delete, so no row of a purged account survives to trip its user_id FK
(their 10-minute TTL means any such row expired weeks before day 31).
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
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

/**
 * Hard-deletes soft-deleted accounts once their recovery window has
 * passed (design doc §2: soft delete = 30-day reuse block, purge on
 * day 31). Until this job runs, a deleted row keeps occupying its
 * unique email/username; the purge is what releases them. Dependent
 * account_tokens rows are removed first, in the same transaction, so
 * the users delete never trips their user_id FK. The run also sweeps
 * pending sign-ups whose code has expired (issue #88), and expired
 * account-update gate codes and pending email changes (issue #92) —
 * those two before the users delete, for their user_id FKs.
 */
@Component
class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final AccountTokenRepository accountTokens;
    private final PendingSignupRepository pendingSignups;
    private final AccountUpdateOtpRepository accountUpdateOtps;
    private final PendingEmailChangeRepository pendingEmailChanges;
    private final UserRepository users;
    private final int retentionDays;

    AccountPurgeScheduler(AccountTokenRepository accountTokens,
            PendingSignupRepository pendingSignups,
            AccountUpdateOtpRepository accountUpdateOtps,
            PendingEmailChangeRepository pendingEmailChanges,
            UserRepository users, @Value("${user.retention.days}") int retentionDays) {
        this.accountTokens = accountTokens;
        this.pendingSignups = pendingSignups;
        this.accountUpdateOtps = accountUpdateOtps;
        this.pendingEmailChanges = pendingEmailChanges;
        this.users = users;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${user.retention.purge-cron}")
    @Transactional
    void purgeExpiredDeletedAccounts() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        int tokenCount = accountTokens.deleteByUserDeletedBefore(cutoff);
        // the #92 rows go before the users delete: a purged account's
        // rows here (necessarily long expired) would trip its user_id FK
        int gateCount = accountUpdateOtps.deleteByExpiresAtBefore(now);
        int emailChangeCount = pendingEmailChanges.deleteByExpiresAtBefore(now);
        int userCount = users.deleteByDeletedBefore(cutoff);
        int pendingCount = pendingSignups.deleteByExpiresAtBefore(now);
        log.info("purged {} account(s) deleted before {} (with {} account token(s))"
                + " and {} expired pending sign-up(s), {} gate code(s),"
                + " {} pending email change(s)",
                userCount, cutoff, tokenCount, pendingCount, gateCount, emailChangeCount);
    }
}
