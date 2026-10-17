/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: unit tests for issue #64's idempotent processor, exercising
 * the EventProcessor port directly with typed events (no broker), per
 * the design doc's "testing follows the port" rule (D11) and
 * requirements F1.2, F2.1. Method-B refactor (D16-D19): stale-discard
 * scenarios removed with the mechanism (author decision D19); the
 * unknown-type scenario moved to the converter test.
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCreated;
import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import foc.notification.repository.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises {@link EventProcessor} directly with typed events — no
 * broker involved (D11: testing follows the port). Runs on H2 with
 * the listener container disabled by the test configuration.
 */
@SpringBootTest
class IdempotentEventProcessorTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-09-19T08:30:00Z");

	@Autowired
	private EventProcessor eventProcessor;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private ProcessedEventRepository processedEvents;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clearDatabase() {
		notifications.deleteAll();
		processedEvents.deleteAll();
	}

	private static RequestAccepted accepted(String eventId, String requestId, List<String> parties) {
		return new RequestAccepted(eventId, OCCURRED_AT, "order-service", "c-1", parties,
				requestId, "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "hi");
	}

	private static RequestCreated created(String eventId, String requestId, List<String> parties) {
		return new RequestCreated(eventId, OCCURRED_AT, "order-service", "c-1", parties,
				requestId, "usr-req-1001", "Techno Edge", "COM3-01-19", "hi");
	}

	@Test
	void createsOneNotificationPerParty() {
		eventProcessor.process(accepted("e-1", "req-1", List.of("usr-req-1001", "usr-cou-2002")));

		List<Notification> rows = notifications.findAll();
		assertThat(rows).hasSize(2);
		assertThat(rows).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder("usr-req-1001", "usr-cou-2002");
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getEventId()).isEqualTo("e-1");
			assertThat(row.getEventType()).isEqualTo("request.accepted");
			assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
			assertThat(row.isRead()).isFalse();
		});
		assertThat(processedEvents.existsById("e-1")).isTrue();
	}

	@Test
	void payloadStoresBusinessFieldsOnly() {
		eventProcessor.process(created("e-1", "req-1", List.of("usr-req-1001")));

		String stored = notifications.findAll().get(0).getPayload();
		Map<String, Object> payload = objectMapper.readValue(stored, Map.class);
		assertThat(payload).containsExactlyInAnyOrderEntriesOf(Map.of(
				"requestId", "req-1",
				"requesterId", "usr-req-1001",
				"pickupLocation", "Techno Edge",
				"dropoffLocation", "COM3-01-19",
				"note", "hi"));
		// Transport metadata never leaks into the client-facing payload.
		assertThat(payload).doesNotContainKeys("eventId", "eventType", "parties",
				"producer", "correlationId", "occurredAt");
	}

	@Test
	void duplicateEventIdIsSilentNoOp() {
		RequestAccepted event = accepted("e-1", "req-1", List.of("usr-req-1001", "usr-cou-2002"));
		eventProcessor.process(event);
		eventProcessor.process(event);

		assertThat(notifications.count()).isEqualTo(2);
	}

	@Test
	void distinctEventsForSameRequestEachNotify() {
		eventProcessor.process(created("e-1", "req-1", List.of("usr-req-1001")));
		eventProcessor.process(accepted("e-2", "req-1", List.of("usr-req-1001")));

		assertThat(notifications.count()).isEqualTo(2);
		assertThat(notifications.findAll()).extracting(Notification::getEventType)
				.containsExactlyInAnyOrder("request.created", "request.accepted");
	}
}
