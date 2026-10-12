/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the scheduled outbox publisher (docs/credit-service.md D10).
 * The poll interval (1 s, as a fixed delay), the batch cap (100), and
 * what a failed publish does to the rest of the poll are the team's
 * decisions.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;

/**
 * Publishes the unsent replies in the outbox, oldest first, and marks
 * each one sent once the broker has confirmed it without returning it
 * (D10). A reply that is not sent stays in the outbox for the next
 * poll, so a reply can be published more than once (a crash between the
 * confirm and the mark, or two instances polling): the Order Service
 * ignores a reply for a request that is no longer pending.
 *
 * <p>Off in tests via {@code credit.outbox.publisher.enabled=false}, so
 * {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "credit.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEvents;
    private final ReplyPublisher publisher;
    private final int batchSize;

    public OutboxPublisher(
            OutboxEventRepository outboxEvents,
            ReplyPublisher publisher,
            @Value("${credit.outbox.publisher.batch-size}") int batchSize) {
        this.outboxEvents = outboxEvents;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    // A fixed delay, not a fixed rate: the next poll starts only after
    // this one has finished, so a slow publish cannot overlap the next
    // poll and send the same reply twice.
    @Scheduled(fixedDelayString = "${credit.outbox.publisher.poll-interval-ms}")
    public void publishUnsent() {
        // capped, so a backlog after an outage drains over several polls
        List<OutboxEvent> unsent = outboxEvents.findBySentAtIsNullOrderByCreatedAtAsc(Limit.of(batchSize));
        for (OutboxEvent reply : unsent) {
            switch (publisher.publish(reply)) {
                case SENT -> {
                    reply.setSentAt(Instant.now());
                    outboxEvents.save(reply);
                }
                // returned as unroutable, which is about this reply alone
                // (the publisher logged it): it stays unsent for the next
                // poll and the rest go on
                case NOT_SENT -> { }
                case BROKER_UNAVAILABLE -> {
                    log.warn("Broker unavailable; outbox poll stopped at reply {} and resumes on the next poll",
                        reply.getEventId());
                    return;
                }
            }
        }
    }
}
