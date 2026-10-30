/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's foc.order.OutboxPublisher (itself
 * credit-service's D10: 1 s fixed-delay poll, 100 per batch, a
 * returned event due again after 30 s, a broker failure stops the
 * poll), with the package and property prefix changed; copied, not
 * shared, with the same values, by the author's decision.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.rating.entity.OutboxEvent;
import foc.rating.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes the unsent events in the outbox that are due, oldest first,
 * and marks each one sent once the broker has confirmed it without
 * returning it. An event that is not sent stays in the outbox: one the
 * broker returned is due again after the retry delay, any other on the
 * next poll. So an event can be published more than once (a crash
 * between the confirm and the mark, or two instances polling): the
 * consumers deduplicate on {@code eventId}.
 *
 * <p>Off in tests via {@code rating.outbox.publisher.enabled=false}, so
 * {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "rating.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEvents;
    private final EventPublisher publisher;
    private final int batchSize;
    private final Duration unroutableRetryDelay;

    public OutboxPublisher(
            OutboxEventRepository outboxEvents,
            EventPublisher publisher,
            @Value("${rating.outbox.publisher.batch-size}") int batchSize,
            @Value("${rating.outbox.publisher.unroutable-retry-delay-ms}") long unroutableRetryDelayMs) {
        this.outboxEvents = outboxEvents;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.unroutableRetryDelay = Duration.ofMillis(unroutableRetryDelayMs);
    }

    // A fixed delay, not a fixed rate: the next poll starts only after
    // this one has finished, so a slow publish cannot overlap the next
    // poll and send the same event twice.
    @Scheduled(fixedDelayString = "${rating.outbox.publisher.poll-interval-ms}")
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
