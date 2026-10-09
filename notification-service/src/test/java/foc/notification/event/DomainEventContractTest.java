/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: consumer-side contract test (successor to issue #63's
 * envelope test) for the Method-B refactor (D16-D19): every
 * registry-cataloged event's canonical fixture must bind to its
 * record, and the tolerant-reader rule for unknown *fields* holds
 * (F1.3). Unknown *types* are converter-level failures now — see
 * DomainEventMessageConverterTest.
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-10-09, Claude Code (Opus 5.5), PR #163 review (Leong Wei Zhi): the catalog
 * now spans the request and credit domains, so the producer and
 * requestId checks go through each event's domain marker, and
 * parties is required present rather than non-empty (credit events
 * notify no one). Author review: Ryan Ang, pending pull request review.
 */
package foc.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.request.RequestAccepted;
import foc.contracts.events.request.RequestEvent;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Contract test for the typed event catalog: each
 * {@link EventTypeRegistry} entry ships a canonical fixture in the
 * foc-contracts jar at
 * {@code /contracts/<eventType dots&rarr;hyphens>.example.json}, and
 * that fixture must bind to the registered record class. Iterating the
 * registry means a newly added event is covered the moment its entry
 * exists — or fails loudly if its fixture is missing. The catalog spans
 * two producing domains (request and credit), so the expected producer
 * and the request reference come from each event's domain marker.
 *
 * <p>{@code @JsonTest} injects Boot's auto-configured
 * {@link ObjectMapper} — the same mapper the AMQP message converter
 * uses (D15) so the behavior proven here holds at the broker boundary.
 * Note the fixtures carry the injected {@code eventType} field the
 * records don't store (D18); binding them successfully is itself proof
 * of tolerant reading.
 */
@JsonTest
class DomainEventContractTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyCatalogedEventHasABindingFixture() throws IOException {
		for (EventTypeRegistry.Entry entry : EventTypeRegistry.entries()) {
			String path = "/contracts/" + entry.eventType().replace('.', '-') + ".example.json";
			DomainEvent event;
			try (InputStream fixture = getClass().getResourceAsStream(path)) {
				assertThat(fixture).as("fixture %s on classpath via foc-contracts jar", path).isNotNull();
				event = objectMapper.readValue(fixture, entry.eventClass());
			}

			assertThat(event.eventType()).isEqualTo(entry.eventType());
			assertThat(event.eventId()).isNotBlank();
			assertThat(event.occurredAt()).isNotNull();
			assertThat(event.producer()).isEqualTo(expectedProducer(event));
			assertThat(event.correlationId()).isNotBlank();
			assertThat(event.parties()).isNotNull();
			assertThat(requestIdOf(event)).isEqualTo("req-20260919-0042");
		}
	}

	private static String expectedProducer(DomainEvent event) {
		if (event instanceof RequestEvent) {
			return "order-service";
		}
		if (event instanceof CreditEvent) {
			return "credit-service";
		}
		throw new AssertionError(event.eventType() + " implements no domain marker interface");
	}

	private static String requestIdOf(DomainEvent event) {
		if (event instanceof RequestEvent requestEvent) {
			return requestEvent.requestId();
		}
		if (event instanceof CreditEvent creditEvent) {
			return creditEvent.requestId();
		}
		throw new AssertionError(event.eventType() + " implements no domain marker interface");
	}

	@Test
	void ignoresUnknownFields() {
		String json = """
				{
				  "eventType": "request.accepted",
				  "eventId": "e-1",
				  "occurredAt": "2026-09-19T08:30:00Z",
				  "producer": "order-service",
				  "correlationId": "c-1",
				  "parties": ["usr-req-1001", "usr-cou-2002"],
				  "requestId": "req-1",
				  "requesterId": "usr-req-1001",
				  "courierId": "usr-cou-2002",
				  "pickupLocation": "Techno Edge",
				  "dropoffLocation": "COM3-01-19",
				  "note": "hi",
				  "someFutureField": "ignored",
				  "someFutureObject": {"nested": true}
				}
				""";

		RequestAccepted event = objectMapper.readValue(json, RequestAccepted.class);

		assertThat(event.eventId()).isEqualTo("e-1");
		assertThat(event.requestId()).isEqualTo("req-1");
		assertThat(event.parties()).containsExactly("usr-req-1001", "usr-cou-2002");
	}
}
