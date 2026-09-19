/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: issue #64's broker integration test (design doc: "one
 * integration test exercises the RabbitMQ adapter, e.g. via
 * Testcontainers"): canonical fixture published as raw JSON to the
 * real exchange, consumed, deduped and acked end to end.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import foc.contracts.events.EventContracts;
import foc.contracts.events.EventEnvelope;
import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * goes to the {@code order-events} exchange; the listener consumes,
 * the processor stores one row per party, duplicates and stale
 * sequences are absorbed, and every message ends acked (queue
 * drained). Skips cleanly when Docker is not available.
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

		// Canonical fixture (eventId 9b2f…, seq 2, two parties), as raw JSON.
		publishRawJson(fixture);
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(notifications.count()).isEqualTo(2));

		List<Notification> rows = notifications.findAll();
		assertThat(rows).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder("usr-req-1001", "usr-cou-2002");
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getEntityType()).isEqualTo("order");
			assertThat(row.getEntityId()).isEqualTo("ord-20260919-0042");
			assertThat(row.getEventType()).isEqualTo("accepted");
		});

		// Duplicate delivery, then a sentinel event: the queue is FIFO, so
		// once the sentinel's row exists the duplicate has been fully
		// processed — and must have produced nothing.
		publishRawJson(fixture);
		publishEnvelope(envelope("e-int-3", 3, "collected"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-int-3")).isEqualTo(1));
		assertThat(notifications.count()).isEqualTo(3);

		// Stale sequence (1 < 3), then another sentinel: no rows for the
		// stale event once the sentinel is through.
		publishEnvelope(envelope("e-int-stale", 1, "created"));
		publishEnvelope(envelope("e-int-4", 4, "completed"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-int-4")).isEqualTo(1));
		assertThat(countByEventId("e-int-stale")).isZero();

		// Everything acked: the work queue drains to zero.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount()).isZero());
	}

	private static byte[] canonicalFixtureBytes() throws IOException {
		try (InputStream fixture = OrderEventsRabbitMqIntegrationTest.class
				.getResourceAsStream("/contracts/order-event.example.json")) {
			assertThat(fixture).isNotNull();
			return fixture.readAllBytes();
		}
	}

	private void publishRawJson(byte[] body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		rabbitTemplate.send(EventContracts.ORDER_EVENTS_EXCHANGE, "",
				new Message(body, properties));
	}

	private void publishEnvelope(EventEnvelope envelope) {
		rabbitTemplate.convertAndSend(EventContracts.ORDER_EVENTS_EXCHANGE, "", envelope);
	}

	private static EventEnvelope envelope(String eventId, long sequence, String eventType) {
		return new EventEnvelope(eventId, "order", "ord-20260919-0042", sequence, eventType,
				Instant.parse("2026-09-19T09:00:00Z"), List.of("usr-req-1001"),
				Map.of("note", "integration"));
	}

	private long countByEventId(String eventId) {
		return notifications.findAll().stream()
				.filter(row -> eventId.equals(row.getEventId()))
				.count();
	}

	private long readyMessageCount() {
		Integer count = rabbitTemplate.execute(channel ->
				channel.queueDeclarePassive(RabbitMqTopology.WORK_QUEUE).getMessageCount());
		return count == null ? -1 : count;
	}
}
