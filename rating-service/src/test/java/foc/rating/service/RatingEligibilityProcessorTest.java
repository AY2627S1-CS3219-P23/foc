/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: port-level tests following notification-service's
 * IdempotentEventProcessorTest (its D11 "testing follows the port"
 * rule): one eligibility row with the mapped fields, the seen-ID
 * no-op, and this service's upsert stance on a repeated requestId
 * under a fresh eventId.
 * Author review: [pending]
 */
package foc.rating.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.request.RequestCompleted;
import foc.rating.entity.RateableRequest;
import foc.rating.repository.ProcessedEventRepository;
import foc.rating.repository.RateableRequestRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises {@link EventProcessor} directly with typed events — no
 * broker involved. Runs on H2 with the listener container disabled by
 * the test configuration.
 */
@SpringBootTest
class RatingEligibilityProcessorTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-19T09:05:00Z");

    @Autowired
    private EventProcessor eventProcessor;

    @Autowired
    private RateableRequestRepository rateableRequests;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @BeforeEach
    void clearDatabase() {
        rateableRequests.deleteAll();
        processedEvents.deleteAll();
    }

    private static RequestCompleted completed(String eventId, String requestId, Instant occurredAt) {
        return new RequestCompleted(eventId, occurredAt, "order-service", "c-1",
                List.of("usr-req-1001"), requestId, "usr-req-1001", "usr-cou-2002",
                "Techno Edge", "COM3-01-19", null);
    }

    @Test
    void storesOneEligibilityRowPerEvent() {
        eventProcessor.process(completed("e-1", "req-1", OCCURRED_AT));

        List<RateableRequest> rows = rateableRequests.findAll();
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getRequestId()).isEqualTo("req-1");
            assertThat(row.getRequesterId()).isEqualTo("usr-req-1001");
            assertThat(row.getCourierId()).isEqualTo("usr-cou-2002");
            // completed_at is the event's occurredAt — the contract has
            // no separate completion timestamp
            assertThat(row.getCompletedAt()).isEqualTo(OCCURRED_AT);
        });
        assertThat(processedEvents.existsById("e-1")).isTrue();
    }

    @Test
    void duplicateEventIdIsSilentNoOp() {
        RequestCompleted event = completed("e-1", "req-1", OCCURRED_AT);
        eventProcessor.process(event);
        eventProcessor.process(event);

        assertThat(rateableRequests.count()).isEqualTo(1);
    }

    @Test
    void repeatedCompletionUnderAFreshEventIdUpsertsTheRow() {
        // a producer bug or corrected replay, not a feature: last write wins
        eventProcessor.process(completed("e-1", "req-1", OCCURRED_AT));
        eventProcessor.process(completed("e-2", "req-1", OCCURRED_AT.plusSeconds(60)));

        assertThat(rateableRequests.count()).isEqualTo(1);
        assertThat(rateableRequests.findById("req-1")).hasValueSatisfying(row ->
                assertThat(row.getCompletedAt()).isEqualTo(OCCURRED_AT.plusSeconds(60)));
    }
}
