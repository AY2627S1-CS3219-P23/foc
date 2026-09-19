/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: unit tests for issue #64's idempotent processor, exercising
 * the EventProcessor port directly with envelopes (no broker), per
 * the design doc's "testing follows the port" rule (D11) and
 * requirements F1.2, F1.3, F2.1, F2.4.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.EventEnvelope;
import foc.notification.entity.EntitySequenceId;
import foc.notification.entity.Notification;
import foc.notification.repository.EntitySequenceRepository;
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
 * Exercises {@link EventProcessor} directly with envelopes — no
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
	private EntitySequenceRepository entitySequences;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clearDatabase() {
		notifications.deleteAll();
		processedEvents.deleteAll();
		entitySequences.deleteAll();
	}

	private static EventEnvelope envelope(String eventId, String entityId, long sequence,
			String eventType, List<String> parties) {
		return new EventEnvelope(eventId, "order", entityId, sequence, eventType,
				OCCURRED_AT, parties, Map.of("note", "hi"));
	}

	@Test
	void createsOneNotificationPerParty() {
		eventProcessor.process(envelope("e-1", "ord-1", 2, "accepted",
				List.of("usr-req-1001", "usr-cou-2002")));

		List<Notification> rows = notifications.findAll();
		assertThat(rows).hasSize(2);
		assertThat(rows).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder("usr-req-1001", "usr-cou-2002");
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getEntityType()).isEqualTo("order");
			assertThat(row.getEntityId()).isEqualTo("ord-1");
			assertThat(row.getEventId()).isEqualTo("e-1");
			assertThat(row.getEventType()).isEqualTo("accepted");
			assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
			assertThat(row.isRead()).isFalse();
		});
		assertThat(processedEvents.existsById("e-1")).isTrue();
		assertThat(entitySequences.findById(new EntitySequenceId("order", "ord-1")))
				.hasValueSatisfying(sequence ->
						assertThat(sequence.getLastAppliedSequence()).isEqualTo(2L));
	}

	@Test
	void serializesPayloadWithSharedMapper() {
		Map<String, Object> payload = Map.of(
				"requesterId", "usr-req-1001",
				"pickupLocation", "Techno Edge");
		eventProcessor.process(new EventEnvelope("e-1", "order", "ord-1", 1, "created",
				OCCURRED_AT, List.of("usr-req-1001"), payload));

		String stored = notifications.findAll().get(0).getPayload();
		Map<String, Object> roundTripped = objectMapper.readValue(stored, Map.class);
		assertThat(roundTripped).isEqualTo(payload);
	}

	@Test
	void duplicateEventIdIsSilentNoOp() {
		EventEnvelope event = envelope("e-1", "ord-1", 2, "accepted",
				List.of("usr-req-1001", "usr-cou-2002"));
		eventProcessor.process(event);
		eventProcessor.process(event);

		assertThat(notifications.count()).isEqualTo(2);
	}

	@Test
	void staleSequenceDiscardedButEventRecorded() {
		eventProcessor.process(envelope("e-1", "ord-1", 2, "accepted", List.of("usr-req-1001")));
		eventProcessor.process(envelope("e-2", "ord-1", 1, "created", List.of("usr-req-1001")));
		eventProcessor.process(envelope("e-3", "ord-1", 2, "accepted", List.of("usr-req-1001")));

		assertThat(notifications.count()).isEqualTo(1);
		assertThat(entitySequences.findById(new EntitySequenceId("order", "ord-1")))
				.hasValueSatisfying(sequence ->
						assertThat(sequence.getLastAppliedSequence()).isEqualTo(2L));
		assertThat(processedEvents.existsById("e-2")).isTrue();
		assertThat(processedEvents.existsById("e-3")).isTrue();
	}

	@Test
	void higherSequenceAdvances() {
		eventProcessor.process(envelope("e-1", "ord-1", 2, "accepted", List.of("usr-req-1001")));
		eventProcessor.process(envelope("e-2", "ord-1", 3, "collected", List.of("usr-req-1001")));

		assertThat(notifications.count()).isEqualTo(2);
		assertThat(entitySequences.findById(new EntitySequenceId("order", "ord-1")))
				.hasValueSatisfying(sequence ->
						assertThat(sequence.getLastAppliedSequence()).isEqualTo(3L));
	}

	@Test
	void unknownEventAndEntityTypesStoredGenerically() {
		eventProcessor.process(new EventEnvelope("e-1", "some-future-entity", "x-1", 1,
				"some-future-type", OCCURRED_AT, List.of("usr-req-1001"), Map.of()));

		List<Notification> rows = notifications.findAll();
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getEntityType()).isEqualTo("some-future-entity");
		assertThat(rows.get(0).getEventType()).isEqualTo("some-future-type");
	}
}
