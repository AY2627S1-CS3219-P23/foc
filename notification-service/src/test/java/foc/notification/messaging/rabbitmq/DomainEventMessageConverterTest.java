/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: unit tests for the Method-B message converter (D16-D19):
 * registry dispatch on the body eventType, eventType injection on
 * serialize, and fatal MessageConversionException on malformed,
 * missing-type, and unknown-type bodies.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import foc.contracts.events.EventTypeRegistry;
import foc.contracts.events.OrderAccepted;
import foc.contracts.events.OrderCancelled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain unit test — no Spring context. Uses a bare {@link JsonMapper}
 * whose relevant behavior (tolerant reading) matches Boot's
 * auto-configured mapper, which {@code DomainEventContractTest} locks
 * in.
 */
class DomainEventMessageConverterTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final DomainEventMessageConverter converter = new DomainEventMessageConverter(jsonMapper);

	private static Message message(byte[] body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		return new Message(body, properties);
	}

	@Test
	void dispatchesFixtureToItsRecordClass() throws IOException {
		byte[] body;
		try (InputStream fixture = getClass().getResourceAsStream("/contracts/order-accepted.example.json")) {
			assertThat(fixture).as("fixture on classpath via foc-contracts jar").isNotNull();
			body = fixture.readAllBytes();
		}

		Object event = converter.fromMessage(message(body));

		assertThat(event).isInstanceOf(OrderAccepted.class);
		OrderAccepted accepted = (OrderAccepted) event;
		assertThat(accepted.orderId()).isEqualTo("ord-20260919-0042");
		assertThat(accepted.eventType()).isEqualTo("order.accepted");
	}

	@Test
	void unknownEventTypeIsFatal() {
		String json = """
				{"eventType": "order.refunded", "eventId": "e-1"}
				""";

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))))
				.withMessageContaining("order.refunded");
	}

	@Test
	void missingEventTypeIsFatal() {
		String json = """
				{"eventId": "e-1", "orderId": "ord-1"}
				""";

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void missingRequiredFieldIsFatal() {
		// Binds structurally (Jackson supplies nulls) but must be
		// rejected before it can reach the processor.
		String json = """
				{"eventType": "order.accepted", "eventId": "e-1"}
				""";

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))))
				.withMessageContaining("missing required field");
	}

	@Test
	void nullableCourierIdIsAcceptedOnOrderCancelled() throws IOException {
		byte[] body;
		try (InputStream fixture = getClass().getResourceAsStream("/contracts/order-cancelled.example.json")) {
			assertThat(fixture).as("fixture on classpath via foc-contracts jar").isNotNull();
			body = fixture.readAllBytes();
		}

		Object event = converter.fromMessage(message(body));

		assertThat(event).isInstanceOf(OrderCancelled.class);
		assertThat(((OrderCancelled) event).courierId()).isNull();
	}

	@Test
	void malformedJsonIsFatal() {
		byte[] body = "not json {{".getBytes(StandardCharsets.UTF_8);

		assertThatExceptionOfType(MessageConversionException.class)
				.isThrownBy(() -> converter.fromMessage(message(body)));
	}

	@Test
	void toMessageInjectsEventTypeAndRoundTrips() {
		OrderAccepted event = new OrderAccepted("e-1", Instant.parse("2026-09-19T08:30:00Z"),
				"order-service", "c-1", List.of("usr-req-1001", "usr-cou-2002"),
				"ord-1", "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "hi");

		Message message = converter.toMessage(event, new MessageProperties());

		assertThat(message.getMessageProperties().getContentType())
				.isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
		Map<String, Object> wire = jsonMapper.readValue(message.getBody(), Map.class);
		// The derived identity is injected; records deliberately don't store it (D18).
		assertThat(wire).containsEntry(DomainEventMessageConverter.EVENT_TYPE_FIELD,
				EventTypeRegistry.routingKeyFor(OrderAccepted.class));

		Object roundTripped = converter.fromMessage(message);
		assertThat(roundTripped).isEqualTo(event);
	}
}
