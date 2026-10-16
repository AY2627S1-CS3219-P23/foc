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
 * PR #193 review (Leong Wei Zhi), 2026-10-16, team decision: one
 * routine, run daily at 00:00 and at startup, sweeps then redistributes
 * the month if its run is missing; the 00:10 job is gone. The tool
 * rewrote the class to it.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * Runs one routine daily at 00:00 Singapore time and once at startup
 * (D12): the expiry sweep, then the redistribution of the current
 * Singapore month if that month has no recorded run. The order inside
 * the routine puts what expired into the pool before it is shared out.
 *
 * <p>A run that fails is not retried in place: the next day's routine
 * finds the month still missing and runs it then. One account that fails
 * to expire is logged and the others still commit; it is visited again
 * the next day. Repeating the routine is safe: the sweep touches only
 * lots past expiry that still hold credits, and a month with a recorded
 * run is not redistributed again.
 *
 * <p>Off in tests via {@code credit.scheduler.enabled=false}, so a test
 * context neither runs at startup nor fires on the clock.
 */
@Component
@ConditionalOnProperty(name = "credit.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryAndRedistributionScheduler {

    static final String ZONE = "Asia/Singapore";

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
    public void runDaily() {
        run();
    }

    // also a month whose run was missed while the service was down
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        run();
    }

    /**
     * The sweep, then the month's redistribution if it is missing.
     * Neither step throws: an exception from the ready-event listener
     * fails the application's startup.
     */
    void run() {
        Instant now = clock.instant();
        try {
            expireLots(now);
        } catch (RuntimeException ex) {
            log.error("expiry run outcome=FAILED", ex);
        }
        redistribute(YearMonth.now(clock), now);
    }

    private void expireLots(Instant now) {
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

    private void redistribute(YearMonth period, Instant now) {
        try {
            RedistributionOutcome outcome = redistribution.redistribute(period, now);
            if (outcome.alreadyRan()) {
                log.info("redistribution period={} outcome=ALREADY_RAN", period);
            } else {
                log.info("redistribution period={} accounts={} distributed={} share={} remainder={}",
                    period, outcome.accounts(), outcome.distributed(), outcome.share(), outcome.remainder());
            }
        } catch (RuntimeException ex) {
            // rolled back, so the month stays unrecorded and the next day's run tries again
            log.error("redistribution period={} outcome=FAILED", period, ex);
        }
    }
}
