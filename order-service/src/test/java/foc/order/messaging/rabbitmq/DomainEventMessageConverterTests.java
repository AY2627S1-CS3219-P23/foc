/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: unit tests for the copied DomainEventMessageConverter on the
 * Order Service's side: an outgoing request event carries its
 * eventType, a credit reply binds to its record, and a reply missing a
 * required field or naming an unknown type is refused.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.messaging.rabbitmq;

import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestSubmitted;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventMessageConverterTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final DomainEventMessageConverter converter = new DomainEventMessageConverter(jsonMapper);

	@Test
	void outgoingEventCarriesItsEventType() {
		RequestSubmitted event = new RequestSubmitted("9b2f6b3c-1e9d-4d55-8f6a-0c2f4a7d9e28",
				Instant.parse("2026-10-15T08:00:00Z"), "order-service", "5", List.of("1"),
				"5", "1", "The Deck", "COM3 Level 2", "Chicken rice", 3);

		Message message = converter.toMessage(event, new MessageProperties());

		JsonNode body = jsonMapper.readTree(message.getBody());
		assertEquals("request.submitted", body.get("eventType").stringValue());
		assertEquals(3, body.get("reward").intValue());
	}

	@Test
	void creditReplyBindsToItsRecord() {
		Object event = converter.fromMessage(message("""
				{"eventType": "credit.reservation-rejected", "eventId": "e1",
				 "occurredAt": "2026-10-15T08:00:01Z", "producer": "credit-service",
				 "correlationId": "5", "parties": [], "requestId": "5", "requesterId": "1",
				 "amount": 3, "reason": "insufficient available credits"}"""));

		CreditReservationRejected rejected = assertInstanceOf(CreditReservationRejected.class, event);
		assertEquals("5", rejected.requestId());
		assertEquals("insufficient available credits", rejected.reason());
	}

	@Test
	void replyMissingARequiredFieldIsRefused() {
		assertThrows(MessageConversionException.class, () -> converter.fromMessage(message("""
				{"eventType": "credit.reserved", "eventId": "e2",
				 "occurredAt": "2026-10-15T08:00:01Z", "producer": "credit-service",
				 "correlationId": "5", "parties": [], "requesterId": "1", "amount": 3}""")));
	}

	@Test
	void unknownEventTypeIsRefused() {
		assertThrows(MessageConversionException.class,
				() -> converter.fromMessage(message("{\"eventType\": \"credit.unheard-of\"}")));
	}

	@Test
	void reservedReplyRoundTrips() {
		CreditReserved reserved = new CreditReserved("e3", Instant.parse("2026-10-15T08:00:01Z"),
				"credit-service", "5", List.of(), "5", "1", 3);

		assertEquals(reserved, converter.fromMessage(converter.toMessage(reserved, new MessageProperties())));
	}

	private static Message message(String json) {
		return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
	}
}
