/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: consumer-side contract test for issue #63 asserting the
 * canonical fixture deserializes and the tolerant-reader rules of
 * docs/notification-service.md ("Extensibility") hold (F1.3); revised
 * same day for the author's decision to restrict envelope fields to
 * event-handling semantics (aggregateType/aggregateId/parties).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.EventEnvelope;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Contract test for the event envelope (issue #63): the canonical
 * fixture at {@code /contracts/order-event.example.json} (shipped in
 * the foc-contracts jar) must deserialize into {@link EventEnvelope},
 * and the consumer must be a tolerant reader — unknown fields and
 * unknown event types are ignored, never an error (F1.3; design doc
 * "Extensibility" rules).
 *
 * <p>{@code @JsonTest} injects Boot's auto-configured
 * {@link ObjectMapper} — the same mapper the AMQP message converter
 * must use (issue #64) so the behavior proven here holds at the broker
 * boundary.
 */
@JsonTest
class EventEnvelopeContractTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void deserializesCanonicalFixture() throws IOException {
		EventEnvelope envelope;
		try (InputStream fixture = getClass().getResourceAsStream("/contracts/order-event.example.json")) {
			assertThat(fixture).as("canonical fixture on classpath via foc-contracts jar").isNotNull();
			envelope = objectMapper.readValue(fixture, EventEnvelope.class);
		}

		assertThat(envelope.eventId()).isEqualTo("9b2f6b3c-1e9d-4d55-8f6a-0c2f4a7d9e21");
		assertThat(envelope.aggregateType()).isEqualTo("order");
		assertThat(envelope.aggregateId()).isEqualTo("ord-20260919-0042");
		assertThat(envelope.sequence()).isEqualTo(2L);
		assertThat(envelope.eventType()).isEqualTo("accepted");
		assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-09-19T08:30:00Z"));
		assertThat(envelope.parties()).containsExactly("usr-req-1001", "usr-cou-2002");
		assertThat(envelope.payload()).containsExactlyInAnyOrderEntriesOf(Map.of(
				"requesterId", "usr-req-1001",
				"courierId", "usr-cou-2002",
				"pickupLocation", "Techno Edge",
				"dropoffLocation", "COM3-01-19",
				"note", "Chicken rice, less chilli"));
	}

	@Test
	void ignoresUnknownFields() {
		String json = """
				{
				  "eventId": "e-1",
				  "aggregateType": "order",
				  "aggregateId": "o-1",
				  "sequence": 1,
				  "eventType": "created",
				  "occurredAt": "2026-09-19T08:00:00Z",
				  "parties": ["usr-req-1001"],
				  "payload": {"note": "hi"},
				  "someFutureField": "ignored",
				  "someFutureObject": {"nested": true}
				}
				""";

		EventEnvelope envelope = objectMapper.readValue(json, EventEnvelope.class);

		assertThat(envelope.eventId()).isEqualTo("e-1");
		assertThat(envelope.sequence()).isEqualTo(1L);
		assertThat(envelope.payload()).containsEntry("note", "hi");
	}

	@Test
	void acceptsUnknownEventAndAggregateTypes() {
		String json = """
				{
				  "eventId": "e-2",
				  "aggregateType": "some-future-aggregate",
				  "aggregateId": "x-1",
				  "sequence": 3,
				  "eventType": "some-future-type",
				  "occurredAt": "2026-09-19T09:00:00Z",
				  "parties": ["usr-req-1001", "usr-cou-2002"],
				  "payload": {}
				}
				""";

		EventEnvelope envelope = objectMapper.readValue(json, EventEnvelope.class);

		assertThat(envelope.aggregateType()).isEqualTo("some-future-aggregate");
		assertThat(envelope.eventType()).isEqualTo("some-future-type");
	}

	@Test
	void deserializesSinglePartyEvent() {
		String json = """
				{
				  "eventId": "e-3",
				  "aggregateType": "order",
				  "aggregateId": "o-2",
				  "sequence": 1,
				  "eventType": "created",
				  "occurredAt": "2026-09-19T07:00:00Z",
				  "parties": ["usr-req-1001"],
				  "payload": {"requesterId": "usr-req-1001", "note": "no courier yet"}
				}
				""";

		EventEnvelope envelope = objectMapper.readValue(json, EventEnvelope.class);

		assertThat(envelope.parties()).containsExactly("usr-req-1001");
	}
}
