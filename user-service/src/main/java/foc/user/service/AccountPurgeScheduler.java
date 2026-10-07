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
2026-10-01 (Claude Code, Opus 5), PR #157 Copilot review: the FK cleanup no
longer rides on that expiry sweep. OTP_TTL and USER_RETENTION_DAYS are
independently configurable, so a TTL longer than the recovery window would
leave a LIVE gate or pending row on a purged account and fail the whole
purge transaction; the purged set's rows are now deleted by user, as the
account_tokens cleanup already did, with the expiry sweep left as hygiene.
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
 * account_tokens, account_update_otps and pending_email_changes rows
 * are removed first, in the same transaction, so the users delete never
 * trips their user_id FKs — by purged user, not by expiry, since a
 * TTL longer than the recovery window could leave a live row behind
 * (PR #157 review). The run also sweeps rows whose code has expired,
 * whoever owns them: pending sign-ups (issue #88) and the two
 * account-update tables (issue #92). That sweep is hygiene only — the
 * flows reject an expired row on sight.
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
        // every dependent of a purged account goes first, selected by that
        // account — not by expiry: a TTL longer than the retention window
        // would leave a live row to trip the user_id FK (PR #157 review)
        int tokenCount = accountTokens.deleteByUserDeletedBefore(cutoff);
        int purgedGateCount = accountUpdateOtps.deleteByUserDeletedBefore(cutoff);
        int purgedChangeCount = pendingEmailChanges.deleteByUserDeletedBefore(cutoff);
        int userCount = users.deleteByDeletedBefore(cutoff);
        // hygiene for the accounts that stay: anything already expired,
        // inclusive of the exact boundary the flows call expired
        int pendingCount = pendingSignups.deleteByExpiresAtBefore(now);
        int gateCount = accountUpdateOtps.deleteByExpiresAtNotAfter(now);
        int emailChangeCount = pendingEmailChanges.deleteByExpiresAtNotAfter(now);
        log.info("purged {} account(s) deleted before {} (with {} account token(s),"
                + " {} gate code(s), {} pending email change(s)) and swept"
                + " {} expired pending sign-up(s), {} expired gate code(s),"
                + " {} expired pending email change(s)",
                userCount, cutoff, tokenCount, purgedGateCount, purgedChangeCount,
                pendingCount, gateCount, emailChangeCount);
    }
}
