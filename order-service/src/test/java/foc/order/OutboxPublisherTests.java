/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: tests for OutboxPublisher against in-memory H2 with a stand-in
 * EventPublisher (no broker): a sent event is marked sent, a returned
 * one is pushed back by the retry delay and left unsent, and a broker
 * failure stops the poll.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-22, Claude Code (Opus 5.5), issue #238: runs against
 * Testcontainers Postgres built by Flyway (extends PostgresTestContainer)
 * instead of H2. Author review: Ryan Ang, pending pull request review.
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: a retried settlement is
 * published again with its original event id (DSP-10). Author review:
 * Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.order.model.OutboxEvent;
import foc.order.model.Requests;
import foc.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboxPublisherTests extends PostgresTestContainer {

	private static final long RETRY_DELAY_MS = 30_000;

	@Autowired
	private RequestsService requestsService;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Test
	void sentEventsAreMarkedSent() {
		drain();
		newRequest();

		poll(event -> EventPublisher.Result.SENT);

		assertTrue(outboxEvents.findDue(Instant.now(), Limit.of(100)).isEmpty());
	}

	@Test
	void returnedEventStaysUnsentUntilTheRetryDelayHasPassed() {
		drain();
		newRequest();
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
		newRequest();
		newRequest();

		List<OutboxEvent> published = poll(event -> EventPublisher.Result.BROKER_UNAVAILABLE);

		assertEquals(1, published.size());
		assertNull(outboxEvents.findById(published.get(0).getEventId()).orElseThrow().getSentAt());
	}

	// DSP-10: the resent message is the same event, so Notification
	// deduplicates it and the users are not told twice
	@Test
	void retriedSettlementIsPublishedAgainWithItsOriginalEventId() {
		drain();
		Long id = newRequest();
		requestsService.timeout(id);
		List<OutboxEvent> first = poll(event -> EventPublisher.Result.SENT);
		OutboxEvent cancelled = first.stream()
				.filter(e -> e.getEventType().equals("request.cancelled")).findFirst().orElseThrow();

		requestsService.retrySettlement(id);
		List<OutboxEvent> again = poll(event -> EventPublisher.Result.SENT);

		assertEquals(1, again.size());
		assertEquals(cancelled.getEventId(), again.get(0).getEventId());
		assertEquals(cancelled.getPayload(), again.get(0).getPayload());
		assertNotNull(outboxEvents.findById(cancelled.getEventId()).orElseThrow().getSentAt());
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

	private Long newRequest() {
		Requests request = new Requests();
		request.setRequesterId(1L);
		request.setSupplierId(2L);
		request.setSupplierName("The Deck");
		request.setDropoffLocation("COM3 Level 2");
		request.setItem("Chicken rice");
		request.setCreditAmount(3);
		request.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
		return requestsService.create(request).getId();
	}
}
