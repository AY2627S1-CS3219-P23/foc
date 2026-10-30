/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: a copy of notification-service's DomainEventMessageConverterTest
 * (Claude Code (Fable 5), 2026-09-19/20, reviewed by Leong Wei Zhi),
 * adapted to this service's one consumed type: the fixture is
 * request-completed, and the nullable case is its optional note.
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestCompleted;
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
 * auto-configured mapper.
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
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/request-completed.example.json")) {
            assertThat(fixture).as("fixture on classpath via foc-contracts jar").isNotNull();
            body = fixture.readAllBytes();
        }

        Object event = converter.fromMessage(message(body));

        assertThat(event).isInstanceOf(RequestCompleted.class);
        RequestCompleted completed = (RequestCompleted) event;
        assertThat(completed.requestId()).isEqualTo("req-20260919-0042");
        assertThat(completed.eventType()).isEqualTo("request.completed");
    }

    @Test
    void unknownEventTypeIsFatal() {
        String json = """
                {"eventType": "request.refunded", "eventId": "e-1"}
                """;

        assertThatExceptionOfType(MessageConversionException.class)
                .isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))))
                .withMessageContaining("request.refunded");
    }

    @Test
    void missingEventTypeIsFatal() {
        String json = """
                {"eventId": "e-1", "requestId": "req-1"}
                """;

        assertThatExceptionOfType(MessageConversionException.class)
                .isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void missingRequiredFieldIsFatal() {
        // Binds structurally (Jackson supplies nulls) but must be
        // rejected before it can reach the processor.
        String json = """
                {"eventType": "request.completed", "eventId": "e-1"}
                """;

        assertThatExceptionOfType(MessageConversionException.class)
                .isThrownBy(() -> converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8))))
                .withMessageContaining("missing required field");
    }

    @Test
    void absentNullableNoteIsAccepted() {
        // note is the contract's one @Nullable component on request.completed
        String json = """
                {"eventType": "request.completed", "eventId": "e-1",
                 "occurredAt": "2026-09-19T09:05:00Z", "producer": "order-service",
                 "correlationId": "c-1", "parties": ["usr-req-1001"],
                 "requestId": "req-1", "requesterId": "usr-req-1001",
                 "courierId": "usr-cou-2002", "pickupLocation": "Techno Edge",
                 "dropoffLocation": "COM3-01-19"}
                """;

        Object event = converter.fromMessage(message(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(event).isInstanceOf(RequestCompleted.class);
        assertThat(((RequestCompleted) event).note()).isNull();
    }

    @Test
    void malformedJsonIsFatal() {
        byte[] body = "not json {{".getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MessageConversionException.class)
                .isThrownBy(() -> converter.fromMessage(message(body)));
    }

    @Test
    void emptyBodyIsFatal() {
        assertThatExceptionOfType(MessageConversionException.class)
                .isThrownBy(() -> converter.fromMessage(message(new byte[0])));
    }

    @Test
    void toMessageInjectsEventTypeAndRoundTrips() {
        RequestCompleted event = new RequestCompleted("e-1", Instant.parse("2026-09-19T09:05:00Z"),
                "order-service", "c-1", List.of("usr-req-1001", "usr-cou-2002"),
                "req-1", "usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "hi");

        Message message = converter.toMessage(event, new MessageProperties());

        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        Map<String, Object> wire = jsonMapper.readValue(message.getBody(), Map.class);
        // The derived identity is injected; records deliberately don't store it (D18).
        assertThat(wire).containsEntry(DomainEventMessageConverter.EVENT_TYPE_FIELD,
                EventTypeRegistry.routingKeyFor(RequestCompleted.class));

        Object roundTripped = converter.fromMessage(message);
        assertThat(roundTripped).isEqualTo(event);
    }
}
