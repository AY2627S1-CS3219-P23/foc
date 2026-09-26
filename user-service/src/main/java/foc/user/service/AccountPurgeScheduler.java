/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-27
Scope: day-31 purge scheduler for issue #93 (design doc §2: "purge on
       day 31"), following notification-service's RetentionPurgeScheduler.
       FK cleanup as bulk deletes inside the purge transaction chosen by
       Leong Wei Zhi via options Q&A (over DB-level ON DELETE CASCADE);
       window and cadence are env-overridable like the notification purge.
Author review: (pending pull request)
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
import foc.user.repository.OtpRepository;
import foc.user.repository.UserRepository;

/**
 * Hard-deletes soft-deleted accounts once their recovery window has
 * passed (design doc §2: soft delete = 30-day reuse block, purge on
 * day 31). Until this job runs, a deleted row keeps occupying its
 * unique email/username; the purge is what releases them. Dependent
 * otps/account_tokens rows are removed first, in the same transaction,
 * so the users delete never trips their user_id FKs.
 */
@Component
class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final OtpRepository otps;
    private final AccountTokenRepository accountTokens;
    private final UserRepository users;
    private final int retentionDays;

    AccountPurgeScheduler(OtpRepository otps, AccountTokenRepository accountTokens,
            UserRepository users, @Value("${user.retention.days}") int retentionDays) {
        this.otps = otps;
        this.accountTokens = accountTokens;
        this.users = users;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${user.retention.purge-cron}")
    @Transactional
    void purgeExpiredDeletedAccounts() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int otpCount = otps.deleteByUserDeletedBefore(cutoff);
        int tokenCount = accountTokens.deleteByUserDeletedBefore(cutoff);
        int userCount = users.deleteByDeletedBefore(cutoff);
        log.info("purged {} account(s) deleted before {} (with {} otp(s), {} account token(s))",
                userCount, cutoff, otpCount, tokenCount);
    }
}
