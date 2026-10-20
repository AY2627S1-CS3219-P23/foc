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
 * 2026-10-17, Claude Code (Fable 5), issue #66: fixtures updated for
 * the display-name fields added to the request event records (D29);
 * no behavior change in this test. Same day, D29 superseded by the
 * author's D30 (options Q&A): the name fields left the records again,
 * and the names now come from the ingest-time UserDirectory lookup —
 * stubbed here per D11, with cases for the enriched payload and for
 * the unknown-user degradation (no name keys stored).
 * 2026-10-19, Claude Code (Fable 5), issue #221: UserDirectory stub and
 * enrichment cases removed with the lookup (D32 supersedes D30/D31) —
 * the payload test now pins IDs-only storage, no name keys ever.
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
		// Transport metadata never leaks into the client-facing payload,
		// and party fields stay IDs — the web hydrates names at read
		// time (D32), so no display-name key is ever stored.
		assertThat(payload).doesNotContainKeys("eventId", "eventType", "parties",
				"producer", "correlationId", "occurredAt",
				"requesterName", "courierName");
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
