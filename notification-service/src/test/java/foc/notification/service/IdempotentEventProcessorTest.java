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
 * 2026-10-30, Claude Code (Fable 5), issue #266: scenarios for the
 * request.dropped prune (author decision D33 via options Q&A) — scope
 * (one recipient, one request), LIKE prefix safety, idempotent
 * redelivery, and the requester's own dropped row surviving it.
 * 2026-10-31, Claude Code (Fable 5), PR #277 review (@Sinnez1):
 * reorder scenarios — a retried drop spares a re-accept's newer rows
 * (the prune's occurredAt bound), and a courier event landing after
 * the drop that covers it stores no courier row (the ingest guard).
 * The redelivery fixture's re-accept moved to a later occurredAt, as
 * a real re-accept is: at the old shared timestamp the new guard
 * (ties go to the drop, like the prune bound) would skip its row.
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestCreated;
import foc.contracts.events.request.RequestDropped;
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
		return accepted(eventId, requestId, OCCURRED_AT, parties);
	}

	private static RequestAccepted accepted(String eventId, String requestId, Instant occurredAt,
			List<String> parties) {
		return new RequestAccepted(eventId, occurredAt, "order-service", "c-1", parties,
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

	// request.dropped: requester-only parties, courierId = the dropper
	private static RequestDropped dropped(String eventId, String requestId, String courierId) {
		return dropped(eventId, requestId, courierId, OCCURRED_AT);
	}

	private static RequestDropped dropped(String eventId, String requestId, String courierId,
			Instant occurredAt) {
		return new RequestDropped(eventId, occurredAt, "order-service", "c-1",
				List.of("usr-req-1001"), requestId, "usr-req-1001", courierId,
				"Techno Edge", "COM3-01-19", "hi");
	}

	// The drop removed the courier as a party, so their rows for that
	// request would only dead-end on the detail 404 (D33, issue #266).
	// The delete is scoped to one recipient and one request: the
	// requester's rows and the courier's rows for other requests stay.
	@Test
	void droppedPrunesTheDroppingCouriersRowsForThatRequestOnly() {
		eventProcessor.process(accepted("e-1", "req-1", List.of("usr-req-1001", "usr-cou-2002")));
		eventProcessor.process(accepted("e-2", "req-2", List.of("usr-req-1001", "usr-cou-2002")));

		eventProcessor.process(dropped("e-3", "req-1", "usr-cou-2002"));

		List<Notification> courierRows = notifications.findAll().stream()
				.filter(n -> n.getRecipientId().equals("usr-cou-2002")).toList();
		assertThat(courierRows).extracting(Notification::getEventId).containsExactly("e-2");

		// the requester keeps their req-1 history and gains the dropped row
		List<Notification> requesterRows = notifications.findAll().stream()
				.filter(n -> n.getRecipientId().equals("usr-req-1001")).toList();
		assertThat(requesterRows).extracting(Notification::getEventId)
				.containsExactlyInAnyOrder("e-1", "e-2", "e-3");
		assertThat(requesterRows).filteredOn(n -> n.getEventId().equals("e-3"))
				.extracting(Notification::getEventType).containsExactly("request.dropped");
	}

	// The pattern carries both quotes around the id, so a drop of
	// request 12 must not take request 123's rows with it.
	@Test
	void droppedPruneDoesNotMatchRequestIdPrefixes() {
		eventProcessor.process(accepted("e-1", "req-123", List.of("usr-cou-2002")));

		eventProcessor.process(dropped("e-2", "req-12", "usr-cou-2002"));

		assertThat(notifications.findAll()).extracting(Notification::getEventId).contains("e-1");
	}

	// A redelivered dropped event is discarded by the dedupe before the
	// prune: a row the courier gained since (e.g. re-accepting the same
	// request — necessarily after the drop, hence the later occurredAt)
	// survives the duplicate.
	@Test
	void redeliveredDroppedEventDoesNotPruneAgain() {
		RequestDropped event = dropped("e-1", "req-1", "usr-cou-2002");
		eventProcessor.process(event);
		eventProcessor.process(accepted("e-2", "req-1", OCCURRED_AT.plusSeconds(5),
				List.of("usr-req-1001", "usr-cou-2002")));

		eventProcessor.process(event);

		assertThat(notifications.findAll()).filteredOn(n -> n.getRecipientId().equals("usr-cou-2002"))
				.extracting(Notification::getEventId).containsExactly("e-2");
		// and the duplicate stored no second requester row
		assertThat(notifications.findAll()).filteredOn(n -> n.getEventId().equals("e-1")).hasSize(1);
	}

	// A transiently failed drop rolls its transaction back (the dedupe
	// never saw it), so the retry-queue redelivery prunes for real — up
	// to 10 s after the courier may have re-accepted. The occurredAt
	// bound keeps the re-accept's newer rows out of its reach (PR #277
	// review).
	@Test
	void retriedDropSparesAReacceptsNewerRows() {
		eventProcessor.process(accepted("e-1", "req-1", OCCURRED_AT,
				List.of("usr-req-1001", "usr-cou-2002")));
		// the re-accept lands before the drop's redelivery does
		eventProcessor.process(accepted("e-2", "req-1", OCCURRED_AT.plusSeconds(10),
				List.of("usr-req-1001", "usr-cou-2002")));

		// the drop sits between the two accepts; its first attempt
		// committed nothing, so this processing runs the prune
		eventProcessor.process(dropped("e-3", "req-1", "usr-cou-2002",
				OCCURRED_AT.plusSeconds(5)));

		assertThat(notifications.findAll()).filteredOn(n -> n.getRecipientId().equals("usr-cou-2002"))
				.extracting(Notification::getEventId).containsExactly("e-2");
	}

	// The reverse reorder: a courier event delayed past the drop that
	// covers it must not store the courier's row — it would re-create
	// exactly the dead-end row the prune removed (PR #277 review). The
	// requester's row stores normally, and a tie goes to the drop, like
	// the prune's own bound.
	@Test
	void courierEventLandingAfterItsDropStoresNoCourierRow() {
		eventProcessor.process(dropped("e-1", "req-1", "usr-cou-2002", OCCURRED_AT.plusSeconds(5)));

		eventProcessor.process(accepted("e-2", "req-1", OCCURRED_AT,
				List.of("usr-req-1001", "usr-cou-2002")));
		eventProcessor.process(accepted("e-3", "req-1", OCCURRED_AT.plusSeconds(5),
				List.of("usr-req-1001", "usr-cou-2002")));

		assertThat(notifications.findAll()).filteredOn(n -> n.getRecipientId().equals("usr-cou-2002"))
				.isEmpty();
		assertThat(notifications.findAll()).filteredOn(n -> n.getRecipientId().equals("usr-req-1001"))
				.extracting(Notification::getEventId)
				.containsExactlyInAnyOrder("e-1", "e-2", "e-3");
		// the guard is per request: the same courier's rows elsewhere
		// store untouched
		eventProcessor.process(accepted("e-4", "req-2", OCCURRED_AT,
				List.of("usr-req-1001", "usr-cou-2002")));
		assertThat(notifications.findAll()).filteredOn(n -> n.getRecipientId().equals("usr-cou-2002"))
				.extracting(Notification::getEventId).containsExactly("e-4");
	}
}
