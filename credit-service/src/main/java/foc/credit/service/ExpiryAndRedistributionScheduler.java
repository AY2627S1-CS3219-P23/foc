/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the expiry and redistribution scheduler (docs/credit-service.md
 * D12). The times and zone (expiry daily at 00:00, redistribution on
 * the 1st at 00:10, Asia/Singapore, which also decides the month), one
 * transaction per account for expiry, and catching up a missed
 * redistribution at startup, after an expiry run so the order matches a
 * normal night, are the team's decisions; the tool wrote the code to
 * them.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import foc.credit.service.RedistributionService.RedistributionOutcome;

/**
 * Runs the expiry daily at 00:00 and the redistribution on the 1st of
 * each month at 00:10, after that day's expiry, both in Singapore time
 * (D12). The month a redistribution is recorded under is the Singapore
 * month it runs in.
 *
 * <p>The expiry run is one transaction per account, so one account
 * that fails is logged and the others still commit; it is visited again
 * the next day. If the service was down at a month's redistribution
 * time, it runs at startup instead, once that time has passed and the
 * month has no recorded run, after an expiry run, as on a normal night.
 *
 * <p>Off in tests via {@code credit.scheduler.enabled=false}, so a test
 * context neither runs a catch-up nor fires on the clock.
 */
@Component
@ConditionalOnProperty(name = "credit.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryAndRedistributionScheduler {

    static final String ZONE = "Asia/Singapore";

    /** On the 1st of the month, after the expiry run at 00:00. */
    static final LocalTime REDISTRIBUTION_TIME = LocalTime.of(0, 10);

    private static final Logger log = LoggerFactory.getLogger(ExpiryAndRedistributionScheduler.class);

    private final LotExpiryService expiry;
    private final RedistributionService redistribution;
    private final Clock clock;

    @Autowired
    public ExpiryAndRedistributionScheduler(LotExpiryService expiry, RedistributionService redistribution) {
        this(expiry, redistribution, Clock.system(ZoneId.of(ZONE)));
    }

    ExpiryAndRedistributionScheduler(LotExpiryService expiry, RedistributionService redistribution, Clock clock) {
        this.expiry = expiry;
        this.redistribution = redistribution;
        this.clock = clock.withZone(ZoneId.of(ZONE));
    }

    @Scheduled(cron = "0 0 0 * * *", zone = ZONE)
    public void expireLots() {
        Instant now = clock.instant();
        List<String> owners = expiry.ownersWithExpiredLots(now);
        int expiredAccounts = 0;
        int moved = 0;
        int failed = 0;
        for (String owner : owners) {
            try {
                int amount = expiry.expireLotsOf(owner, now);
                if (amount > 0) {
                    expiredAccounts++;
                    moved += amount;
                    log.info("expiry user={} amount={}", owner, amount);
                }
            } catch (RuntimeException ex) {
                // rolled back for this account alone; tried again on the next run
                failed++;
                log.error("expiry user={} outcome=FAILED", owner, ex);
            }
        }
        log.info("expiry run accounts={} amount={} failed={}", expiredAccounts, moved, failed);
    }

    @Scheduled(cron = "0 10 0 1 * *", zone = ZONE)
    public void redistributePool() {
        redistribute(YearMonth.now(clock));
    }

    // a month whose run was missed while the service was down
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpMissedRedistribution() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime due = now.withDayOfMonth(1).with(REDISTRIBUTION_TIME);
        if (now.isBefore(due)) {
            // this month's scheduled run is still to come
            return;
        }
        // The same order as a normal night, so credits that expired while
        // the service was down are in the pool before it is shared out.
        // Repeating the expiry is safe: with nothing past expiry it moves
        // nothing. Neither may throw: an exception from a ready-event
        // listener fails the application's startup.
        try {
            expireLots();
        } catch (RuntimeException ex) {
            log.error("expiry run before the redistribution catch-up outcome=FAILED", ex);
        }
        redistribute(YearMonth.from(now));
    }

    private void redistribute(YearMonth period) {
        try {
            RedistributionOutcome outcome = redistribution.redistribute(period, clock.instant());
            if (outcome.alreadyRan()) {
                log.info("redistribution period={} outcome=ALREADY_RAN", period);
            } else {
                log.info("redistribution period={} accounts={} distributed={} share={} remainder={}",
                    period, outcome.accounts(), outcome.distributed(), outcome.share(), outcome.remainder());
            }
        } catch (RuntimeException ex) {
            log.error("redistribution period={} outcome=FAILED", period, ex);
        }
    }
}
