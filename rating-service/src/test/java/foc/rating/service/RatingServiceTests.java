/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #252.
 * Scope: tests of the submit flow's outbox side, following
 * order-service's RequestsServiceTests (read the rows back, parse the
 * payload): the wire body in both directions (parties = the ratee
 * alone, no write-up in the payload, D32), the same-transaction
 * guarantee (a rollback leaves neither row), and the unique key the
 * pre-check leans on.
 * Author review: [pending]
 */
package foc.rating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import foc.rating.entity.RateableRequest;
import foc.rating.entity.Rating;
import foc.rating.repository.RateableRequestRepository;
import foc.rating.repository.RatingRepository;
import foc.rating.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class RatingServiceTests {

    private static final String REQUESTER = "usr-req-1001";
    private static final String COURIER = "usr-cou-2002";

    @Autowired
    private RatingService ratingService;

    @Autowired
    private RateableRequestRepository rateableRequests;

    @Autowired
    private RatingRepository ratings;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String completedRequest(String requestId) {
        rateableRequests.save(new RateableRequest(requestId, REQUESTER, COURIER,
                Instant.parse("2026-09-19T09:30:00Z")));
        return requestId;
    }

    // the test classes share one H2 database, so events are matched by
    // their requestId, never counted globally
    private List<JsonNode> eventsFor(String requestId) {
        return outboxEvents.findAll().stream()
                .sorted(Comparator.comparing(foc.rating.entity.OutboxEvent::getCreatedAt))
                .map(e -> jsonMapper.readTree(e.getPayload()))
                .filter(e -> requestId.equals(e.get("requestId").stringValue()))
                .toList();
    }

    @Test
    @DisplayName("One submit writes one rating.submitted with the mapped fields, the ratee alone in parties")
    void submitWritesRatingSubmittedWithTheMappedFields() {
        String requestId = completedRequest("req-svc-1");

        Rating rating = ratingService.submit(REQUESTER, requestId, 5, "Fast and friendly");

        List<JsonNode> events = eventsFor(requestId);
        assertThat(events).hasSize(1);
        JsonNode event = events.get(0);
        assertThat(event.get("eventType").stringValue()).isEqualTo("rating.submitted");
        assertThat(event.get("producer").stringValue()).isEqualTo("rating-service");
        assertThat(event.get("correlationId").stringValue()).isEqualTo(requestId);
        assertThat(event.get("eventId").stringValue()).isNotBlank();
        assertThat(event.get("occurredAt").isNull()).isFalse();
        assertThat(event.get("ratingId").stringValue()).isEqualTo(rating.getId().toString());
        assertThat(event.get("raterId").stringValue()).isEqualTo(REQUESTER);
        assertThat(event.get("rateeId").stringValue()).isEqualTo(COURIER);
        assertThat(event.get("stars").intValue()).isEqualTo(5);
        // the ratee alone is notified (#44); the rater is not told of
        // their own action
        assertThat(event.get("parties")).hasSize(1);
        assertThat(event.get("parties").get(0).stringValue()).isEqualTo(COURIER);
        // IDs only (D32): the write-up never rides in the payload
        assertThat(event.has("text")).isFalse();
        assertThat(event.has("body")).isFalse();
    }

    @Test
    @DisplayName("The courier's submit flips the ratee")
    void courierSubmitFlipsTheRatee() {
        String requestId = completedRequest("req-svc-2");

        ratingService.submit(COURIER, requestId, 3, null);

        JsonNode event = eventsFor(requestId).get(0);
        assertThat(event.get("raterId").stringValue()).isEqualTo(COURIER);
        assertThat(event.get("rateeId").stringValue()).isEqualTo(REQUESTER);
        assertThat(event.get("parties").get(0).stringValue()).isEqualTo(REQUESTER);
    }

    @Test
    @DisplayName("A rollback after submit leaves neither the rating nor the event")
    void rollbackLeavesNeitherRowBehind() {
        String requestId = completedRequest("req-svc-3");

        // submit joins the surrounding transaction (REQUIRED), so the
        // throw after it must take both inserts down with it
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    ratingService.submit(REQUESTER, requestId, 5, "Lost to a rollback");
                    throw new IllegalStateException("simulated failure after submit");
                }));

        assertThat(ratings.findByRequestIdAndRaterId(requestId, REQUESTER)).isEmpty();
        assertThat(eventsFor(requestId)).isEmpty();
    }

    @Test
    @DisplayName("The unique (request_id, rater_id) key refuses a duplicate that slips past the pre-check")
    void uniqueKeyBacksThePreCheck() {
        String requestId = completedRequest("req-svc-4");
        ratings.saveAndFlush(new Rating(requestId, REQUESTER, COURIER, (short) 5, null, Instant.now()));

        // a concurrent duplicate bypasses the service's friendly 409
        // pre-check; the constraint answers it (the advice maps this
        // exception to the same 409)
        assertThrows(DataIntegrityViolationException.class, () ->
                ratings.saveAndFlush(new Rating(requestId, REQUESTER, COURIER, (short) 1, null, Instant.now())));
    }
}
