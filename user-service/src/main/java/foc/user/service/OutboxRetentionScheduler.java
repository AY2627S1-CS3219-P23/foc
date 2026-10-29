/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 review (team decision): a copy of credit-service's
       OutboxRetentionScheduler (30-day window, never an unsent row, a
       window below 1 refused at startup), with the package and property
       prefix changed; copied, not shared. The cron has no zone, like the
       account purge's.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import foc.user.repository.OutboxEventRepository;

/**
 * Deletes the user events that were sent longer ago than the retention
 * window. A sent event has done its job, and nothing reads it again: the
 * publisher reads only unsent rows. An unsent event is kept however old
 * it is: credit-service has not heard of it yet.
 *
 * <p>Off in tests via {@code user.outbox.retention.enabled=false}, so the
 * clock cannot purge rows a test has just stored.
 */
@Component
@ConditionalOnProperty(name = "user.outbox.retention.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionScheduler.class);

    private final OutboxEventRepository outboxEvents;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public OutboxRetentionScheduler(OutboxEventRepository outboxEvents,
            @Value("${user.outbox.retention.days}") int retentionDays) {
        this(outboxEvents, retentionDays, Clock.systemUTC());
    }

    OutboxRetentionScheduler(OutboxEventRepository outboxEvents, int retentionDays, Clock clock) {
        // a window of 0 would purge every sent event on the first run
        if (retentionDays < 1) {
            throw new IllegalArgumentException(
                "user.outbox.retention.days must be at least 1, was " + retentionDays);
        }
        this.outboxEvents = outboxEvents;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    @Scheduled(cron = "${user.outbox.retention.purge-cron}")
    public void purgeSentEvents() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int deleted = outboxEvents.deleteSentBefore(cutoff);
        log.info("outbox retention purged={} sentBefore={}", deleted, cutoff);
    }
}
