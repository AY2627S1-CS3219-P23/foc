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
2026-10-11 (Claude Code, Fable 5), issue #94: the hygiene sweep covers
expired reset-code rows too, now that the password-reset flow writes
them (password_reset_otps — V7 remakes the account_tokens scaffold).
Same day, PR #179 Copilot review: the purge now locks the users it
will delete BEFORE touching any child table, so it takes locks in the
flows' user-then-child order — unlocked, its child bulk deletes could
hold an OTP row while a reset request held that user and waited for
the same row, a deadlock.
2026-10-26 (Claude Code, Sonnet 5.5), issue #225 (N8.5, D12): before
deleting anything the run asks supplier-service to drop the condemned
accounts' bookmarks (SupplierBookmarkCleanupClient). If that fails, the
run skips the account delete and its child deletes — the accounts stay and
are tried again on the next run — but still does the expired-code hygiene
sweeps. Failure handling chosen by the author.
Same day, PR review: the call is made BEFORE the locking transaction opens,
not inside it — it is idempotent, and holding row locks and a connection for
up to a timeout per batch served nothing: an account past the retention
window cannot be recovered (recoverOwnAccount refuses it), so nothing can
change the condemned set between the call and the delete. The locks stay,
taken first in the short transaction, for the flows' lock order.
*/

package foc.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import foc.user.exception.BookmarkCleanupException;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PasswordResetOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

/**
 * Hard-deletes soft-deleted accounts once their recovery window has
 * passed (design doc §2: soft delete = 30-day reuse block, purge on
 * day 31). Until this job runs, a deleted row keeps occupying its
 * unique email/username; the purge is what releases them. Dependent
 * password_reset_otps, account_update_otps and pending_email_changes rows
 * are removed first, in the same transaction, so the users delete never
 * trips their user_id FKs — by purged user, not by expiry, since a
 * TTL longer than the recovery window could leave a live row behind
 * (PR #157 review). The run also sweeps rows whose code has expired,
 * whoever owns them: pending sign-ups (issue #88), the two
 * account-update tables (issue #92) and reset codes (issue #94). That
 * sweep is hygiene only — the flows reject an expired row on sight.
 * Each purged account's supplier bookmarks are removed first, through
 * supplier-service (issue #225); if that fails the accounts are kept for
 * the next run.
 */
@Component
class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final PasswordResetOtpRepository resetOtps;
    private final PendingSignupRepository pendingSignups;
    private final AccountUpdateOtpRepository accountUpdateOtps;
    private final PendingEmailChangeRepository pendingEmailChanges;
    private final UserRepository users;
    private final SupplierBookmarkCleanupClient bookmarkCleanup;
    private final TransactionTemplate transaction;
    private final int retentionDays;

    AccountPurgeScheduler(PasswordResetOtpRepository resetOtps,
            PendingSignupRepository pendingSignups,
            AccountUpdateOtpRepository accountUpdateOtps,
            PendingEmailChangeRepository pendingEmailChanges,
            UserRepository users, SupplierBookmarkCleanupClient bookmarkCleanup,
            PlatformTransactionManager transactionManager,
            @Value("${user.retention.days}") int retentionDays) {
        this.resetOtps = resetOtps;
        this.pendingSignups = pendingSignups;
        this.accountUpdateOtps = accountUpdateOtps;
        this.pendingEmailChanges = pendingEmailChanges;
        this.users = users;
        this.bookmarkCleanup = bookmarkCleanup;
        this.transaction = new TransactionTemplate(transactionManager);
        this.retentionDays = retentionDays;
    }

    // Not transactional itself: the supplier-service call must not run while
    // row locks and a connection are held. Past the retention window an
    // account cannot be recovered, so the ids read here are still the
    // accounts the transaction below deletes (the cutoff is fixed for the run).
    @Scheduled(cron = "${user.retention.purge-cron}")
    void purgeExpiredDeletedAccounts() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        // supplier-service first (issue #225): once the users are gone
        // nothing would tell it which bookmarks to drop. All or nothing — a
        // failed run leaves every condemned account for the next one, and
        // the call is safe to repeat
        boolean cleaned = bookmarksCleaned(users.findIdsDeletedBefore(cutoff));
        transaction.executeWithoutResult(status -> purge(now, cutoff, cleaned));
    }

    private void purge(Instant now, Instant cutoff, boolean cleaned) {
        // lock the condemned user rows FIRST (PR #179 Copilot review):
        // the flows lock user-then-child, and the child deletes below
        // acquire child-row locks — taken unlocked-first, this
        // transaction could hold a reset OTP row while a reset request
        // held its user and waited for that row, deadlocking both. The
        // locked set is discarded: only the lock order matters
        users.findWithLockByDeletedBefore(cutoff);
        int resetByUserCount = 0;
        int purgedGateCount = 0;
        int purgedChangeCount = 0;
        int userCount = 0;
        if (cleaned) {
            // every dependent of a purged account goes first, selected by that
            // account — not by expiry: a TTL longer than the retention window
            // would leave a live row to trip the user_id FK (PR #157 review)
            resetByUserCount = resetOtps.deleteByUserDeletedBefore(cutoff);
            purgedGateCount = accountUpdateOtps.deleteByUserDeletedBefore(cutoff);
            purgedChangeCount = pendingEmailChanges.deleteByUserDeletedBefore(cutoff);
            userCount = users.deleteByDeletedBefore(cutoff);
        }
        // hygiene for the accounts that stay: anything already expired,
        // inclusive of the exact boundary the flows call expired
        int pendingCount = pendingSignups.deleteByExpiresAtBefore(now);
        int gateCount = accountUpdateOtps.deleteByExpiresAtNotAfter(now);
        int emailChangeCount = pendingEmailChanges.deleteByExpiresAtNotAfter(now);
        int expiredResetCount = resetOtps.deleteByExpiresAtNotAfter(now);
        log.info("purged {} account(s) deleted before {} (with {} reset code(s),"
                + " {} gate code(s), {} pending email change(s)) and swept"
                + " {} expired pending sign-up(s), {} expired gate code(s),"
                + " {} expired pending email change(s), {} expired reset code(s)",
                userCount, cutoff, resetByUserCount, purgedGateCount, purgedChangeCount,
                pendingCount, gateCount, emailChangeCount, expiredResetCount);
    }

    // true when there is nothing to clean or supplier-service did it
    private boolean bookmarksCleaned(List<Long> condemnedIds) {
        if (condemnedIds.isEmpty()) {
            return true;
        }
        try {
            bookmarkCleanup.purgeBookmarks(condemnedIds);
            return true;
        } catch (BookmarkCleanupException e) {
            log.error("skipping the purge of {} account(s): their supplier bookmarks could"
                    + " not be removed; the accounts are kept and tried again on the next run",
                    condemnedIds.size(), e);
            return false;
        }
    }
}
