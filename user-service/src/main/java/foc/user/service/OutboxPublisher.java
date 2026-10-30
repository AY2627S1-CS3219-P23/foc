/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 (team decision USR-2): a copy of order-service's
       OutboxPublisher (1 s fixed-delay poll, 100 per batch, a returned
       event due again after 30 s, a broker failure stops the poll), with
       the package and property prefix changed; copied, not shared, with
       the same values.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import foc.user.entity.OutboxEvent;
import foc.user.repository.OutboxEventRepository;

/**
 * Publishes the unsent events in the outbox that are due, oldest first,
 * and marks each one sent once the broker has confirmed it without
 * returning it. An event that is not sent stays in the outbox: one the
 * broker returned is due again after the retry delay, any other on the
 * next poll. So an event can be published more than once (a crash
 * between the confirm and the mark, or two instances polling): the
 * consumer treats a repeat by the account's state (USR-6).
 *
 * <p>Off in tests via {@code user.outbox.publisher.enabled=false}, so
 * {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "user.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEvents;
    private final EventPublisher publisher;
    private final int batchSize;
    private final Duration unroutableRetryDelay;

    public OutboxPublisher(
            OutboxEventRepository outboxEvents,
            EventPublisher publisher,
            @Value("${user.outbox.publisher.batch-size}") int batchSize,
            @Value("${user.outbox.publisher.unroutable-retry-delay-ms}") long unroutableRetryDelayMs) {
        this.outboxEvents = outboxEvents;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.unroutableRetryDelay = Duration.ofMillis(unroutableRetryDelayMs);
    }

    // A fixed delay, not a fixed rate: the next poll starts only after
    // this one has finished, so a slow publish cannot overlap the next
    // poll and send the same event twice.
    @Scheduled(fixedDelayString = "${user.outbox.publisher.poll-interval-ms}")
    public void publishUnsent() {
        // capped, so a backlog after an outage drains over several polls
        List<OutboxEvent> due = outboxEvents.findDue(Instant.now(), Limit.of(batchSize));
        for (OutboxEvent event : due) {
            switch (publisher.publish(event)) {
                case SENT -> {
                    event.setSentAt(Instant.now());
                    outboxEvents.save(event);
                }
                // returned as unroutable, which is about this event alone
                // (the publisher logged it): it stays unsent and the rest
                // go on, and it is not due again until the delay has passed
                case NOT_SENT -> {
                    event.setNextAttemptAt(Instant.now().plus(unroutableRetryDelay));
                    outboxEvents.save(event);
                }
                case BROKER_UNAVAILABLE -> {
                    log.warn("Broker unavailable; outbox poll stopped at event {} and resumes on the next poll",
                            event.getEventId());
                    return;
                }
            }
        }
    }
}
