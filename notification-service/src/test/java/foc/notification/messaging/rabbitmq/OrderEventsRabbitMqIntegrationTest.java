/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: issue #64's broker integration test (design doc: "one
 * integration test exercises the RabbitMQ adapter, e.g. via
 * Testcontainers"), reworked for the Method-B refactor (D16-D19):
 * topic routing, typed dispatch on the body eventType, dedupe,
 * unknown-type rejection, and full drain end to end.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import foc.contracts.events.EventContracts;
import foc.contracts.events.EventTypeRegistry;
import foc.contracts.events.OrderCollected;
import foc.contracts.events.OrderCompleted;
import foc.contracts.events.OrderEvent;
import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end through a real broker: raw fixture JSON (no broker type
 * headers — the wire contract as the Order Service will publish it)
 * goes to the {@code order-events} <strong>topic</strong> exchange
 * under its canonical routing key; the listener consumes, the
 * converter dispatches on the body {@code eventType} (D18), the
 * processor stores one row per party, duplicates are absorbed,
 * unknown event types are rejected (dropped until #65's DLQ), keys
 * outside {@code order.#} never reach the queue, and every message
 * ends settled (queue drained). Skips cleanly when Docker is not
 * available.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=true",
		// Own H2 instance so this context never shares tables with the
		// cached broker-free context used by the other tests.
		"spring.datasource.url=jdbc:h2:mem:notification-it;DB_CLOSE_DELAY=-1"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderEventsRabbitMqIntegrationTest {

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationRepository notifications;

	@Test
	void consumesDeduplicatesAndAcksThroughRealBroker() throws IOException {
		byte[] fixture = canonicalFixtureBytes();

		// Canonical order-accepted fixture (two parties), as raw JSON
		// under its canonical routing key.
		publishRawJson(fixture, "order.accepted");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(notifications.count()).isEqualTo(2));

		List<Notification> rows = notifications.findAll();
		assertThat(rows).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder("usr-req-1001", "usr-cou-2002");
		assertThat(rows).allSatisfy(row ->
				assertThat(row.getEventType()).isEqualTo("order.accepted"));

		// Duplicate delivery, then a sentinel event: the queue is FIFO, so
		// once the sentinel's row exists the duplicate has been fully
		// processed — and must have produced nothing.
		publishRawJson(fixture, "order.accepted");
		publishEvent(collected("e-int-3"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-int-3")).isEqualTo(1));
		assertThat(notifications.count()).isEqualTo(3);

		// Topic routing: a key outside order.# never reaches this queue —
		// the body is a perfectly valid order event, so a row for it would
		// prove a routing leak, not a conversion failure.
		publishRawJson(validBodyWithEventId("e-int-credit"), "credit.granted");

		// Unknown body eventType under a binding-matching key: fatal
		// conversion failure, rejected without requeue (dropped until
		// #65's DLQ) — must produce no rows and must not wedge the queue.
		publishRawJson("""
				{"eventType": "order.refunded", "eventId": "e-int-unknown",
				 "parties": ["usr-req-1001"]}
				""".getBytes(StandardCharsets.UTF_8), "order.refunded");

		// Closing sentinel: once it lands, everything above is settled.
		publishEvent(completed("e-int-4"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-int-4")).isEqualTo(1));
		assertThat(countByEventId("e-int-credit")).isZero();
		assertThat(countByEventId("e-int-unknown")).isZero();

		// Everything settled: the work queue drains to zero.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount()).isZero());
	}

	private static byte[] canonicalFixtureBytes() throws IOException {
		try (InputStream fixture = OrderEventsRabbitMqIntegrationTest.class
				.getResourceAsStream("/contracts/order-accepted.example.json")) {
			assertThat(fixture).isNotNull();
			return fixture.readAllBytes();
		}
	}

	private void publishRawJson(byte[] body, String routingKey) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		rabbitTemplate.send(EventContracts.ORDER_EVENTS_EXCHANGE, routingKey,
				new Message(body, properties));
	}

	/** Producer convention (D18): routing key from the registry, by class. */
	private void publishEvent(OrderEvent event) {
		rabbitTemplate.convertAndSend(EventContracts.ORDER_EVENTS_EXCHANGE,
				EventTypeRegistry.routingKeyFor(event.getClass()), event);
	}

	private static OrderCollected collected(String eventId) {
		return new OrderCollected(eventId, 1, Instant.parse("2026-09-19T09:00:00Z"),
				"order-service", "c-int", List.of("usr-req-1001"), "ord-20260919-0042",
				"usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "integration");
	}

	private static OrderCompleted completed(String eventId) {
		return new OrderCompleted(eventId, 1, Instant.parse("2026-09-19T09:05:00Z"),
				"order-service", "c-int", List.of("usr-req-1001"), "ord-20260919-0042",
				"usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "integration");
	}

	private static byte[] validBodyWithEventId(String eventId) {
		return ("""
				{"eventType": "order.created", "eventId": "%s",
				 "schemaVersion": 1, "occurredAt": "2026-09-19T09:00:00Z",
				 "producer": "order-service", "correlationId": "c-int",
				 "parties": ["usr-req-1001"], "orderId": "ord-20260919-0042",
				 "requesterId": "usr-req-1001", "pickupLocation": "Techno Edge",
				 "dropoffLocation": "COM3-01-19", "note": "integration"}
				""".formatted(eventId)).getBytes(StandardCharsets.UTF_8);
	}

	private long countByEventId(String eventId) {
		return notifications.findAll().stream()
				.filter(row -> eventId.equals(row.getEventId()))
				.count();
	}

	private long readyMessageCount() {
		Integer count = rabbitTemplate.execute(channel ->
				channel.queueDeclarePassive(RabbitMqTopology.ORDER_EVENTS_QUEUE).getMessageCount());
		return count == null ? -1 : count;
	}
}
