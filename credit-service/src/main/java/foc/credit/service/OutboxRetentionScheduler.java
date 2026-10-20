/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-19, issue #218.
 * Scope: the outbox retention purge (docs/credit-service.md D13),
 * following notification-service's RetentionPurgeScheduler. Which rows
 * go (sent ones only), the column the window counts from (sent_at), the
 * window and schedule (30 days, daily at 03:00 Singapore time, both
 * configuration), purging outbox_event and nothing else, and refusing a
 * window below 1 day at startup are the team's decisions; the tool
 * wrote the code to them.
 * Same day, PR #224 review (Leong Wei Zhi): off in tests, like the
 * other two scheduled components, the reviewer's suggestion.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

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

import foc.credit.repository.OutboxEventRepository;

/**
 * Deletes the replies that were sent longer ago than the retention
 * window (D13). A sent reply has done its job, and nothing reads it
 * again: the publisher reads only unsent rows, and duplicates are caught
 * by the reservation record, not by the outbox. An unsent reply is kept
 * however old it is. Nothing but {@code outbox_event} is purged.
 *
 * <p>Off in tests via {@code credit.outbox.retention.enabled=false}, so
 * the clock cannot purge rows a test has just stored.
 */
@Component
@ConditionalOnProperty(name = "credit.outbox.retention.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionScheduler.class);

    private final OutboxEventRepository outboxEvents;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public OutboxRetentionScheduler(OutboxEventRepository outboxEvents,
            @Value("${credit.outbox.retention.days}") int retentionDays) {
        this(outboxEvents, retentionDays, Clock.systemUTC());
    }

    OutboxRetentionScheduler(OutboxEventRepository outboxEvents, int retentionDays, Clock clock) {
        // a window of 0 would purge every sent reply on the first run
        if (retentionDays < 1) {
            throw new IllegalArgumentException(
                "credit.outbox.retention.days must be at least 1, was " + retentionDays);
        }
        this.outboxEvents = outboxEvents;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    @Scheduled(cron = "${credit.outbox.retention.purge-cron}", zone = ExpiryAndRedistributionScheduler.ZONE)
    public void purgeSentReplies() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int deleted = outboxEvents.deleteSentBefore(cutoff);
        log.info("outbox retention purged={} sentBefore={}", deleted, cutoff);
    }
}
