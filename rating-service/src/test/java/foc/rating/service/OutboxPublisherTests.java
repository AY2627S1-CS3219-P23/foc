/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: a copy of order-service's OutboxPublisherTests without its
 * retry-settlement case (DSP-10, not copied here), against H2 with a
 * stand-in EventPublisher (no broker): a sent event is marked sent, a
 * returned one is pushed back by the retry delay and left unsent, and
 * a broker failure stops the poll. Events are seeded through the real
 * submit flow.
 * Author review: [pending]
 */
package foc.rating.service;

import foc.rating.entity.RateableRequest;
import foc.rating.entity.OutboxEvent;
import foc.rating.repository.OutboxEventRepository;
import foc.rating.repository.RateableRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OutboxPublisherTests {

    private static final long RETRY_DELAY_MS = 30_000;

    private static final String REQUESTER = "usr-req-1001";
    private static final String COURIER = "usr-cou-2002";

    // unique request ids across the class: the cached context shares
    // one H2 database with the other test classes
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RatingService ratingService;

    @Autowired
    private RateableRequestRepository rateableRequests;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Test
    void sentEventsAreMarkedSent() {
        drain();
        newRatingEvent();

        poll(event -> EventPublisher.Result.SENT);

        assertTrue(outboxEvents.findDue(Instant.now(), Limit.of(100)).isEmpty());
    }

    @Test
    void returnedEventStaysUnsentUntilTheRetryDelayHasPassed() {
        drain();
        newRatingEvent();
        Instant before = Instant.now();

        List<OutboxEvent> published = poll(event -> EventPublisher.Result.NOT_SENT);

        assertEquals(1, published.size());
        for (OutboxEvent event : published) {
            OutboxEvent stored = outboxEvents.findById(event.getEventId()).orElseThrow();
            assertNull(stored.getSentAt());
            assertFalse(stored.getNextAttemptAt().isBefore(before.plusMillis(RETRY_DELAY_MS)));
        }
        // not due again before the delay
        assertTrue(outboxEvents.findDue(Instant.now(), Limit.of(100)).isEmpty());
    }

    @Test
    void brokerFailureStopsThePollAtTheFirstEvent() {
        drain();
        newRatingEvent();
        newRatingEvent();

        List<OutboxEvent> published = poll(event -> EventPublisher.Result.BROKER_UNAVAILABLE);

        assertEquals(1, published.size());
        assertNull(outboxEvents.findById(published.get(0).getEventId()).orElseThrow().getSentAt());
    }

    // The test classes share one database, and the others leave unsent
    // events behind: send everything due first, so each test sees only
    // its own events.
    private void drain() {
        while (!poll(event -> EventPublisher.Result.SENT).isEmpty()) {
        }
    }

    // Runs one poll with the given stand-in and returns the events it was handed.
    private List<OutboxEvent> poll(EventPublisher stub) {
        List<OutboxEvent> handed = new ArrayList<>();
        EventPublisher recording = event -> {
            handed.add(event);
            return stub.publish(event);
        };
        new OutboxPublisher(outboxEvents, recording, 100, RETRY_DELAY_MS).publishUnsent();
        return handed;
    }

    // one completed request, one rating, one outbox event — through the
    // real flow, so the poll sees exactly what production writes
    private void newRatingEvent() {
        String requestId = "req-pub-" + SEQUENCE.incrementAndGet();
        rateableRequests.save(new RateableRequest(requestId, REQUESTER, COURIER,
                Instant.parse("2026-09-19T09:30:00Z")));
        ratingService.submit(REQUESTER, requestId, 5, null);
    }
}
