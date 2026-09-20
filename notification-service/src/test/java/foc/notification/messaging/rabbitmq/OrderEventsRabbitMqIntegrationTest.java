/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: issue #64's broker integration test (design doc: "one
 * integration test exercises the RabbitMQ adapter, e.g. via
 * Testcontainers"), reworked for the Method-B refactor (D16-D19):
 * topic routing, typed dispatch on the body eventType, dedupe,
 * unknown-type rejection, and full drain end to end.
 * 2026-09-20, issue #65: retry/DLQ coverage — failure-injecting
 * processor decorator, transient-failure recovery through the retry
 * queue, poison event to the DLQ after the attempt cap (attempt-header
 * + x-death evidence, no wall-clock assertions; this test surfaced
 * that RabbitMQ 4 resets x-death counts on client republish),
 * unknown-type assertions updated from "dropped" to "dead-lettered
 * intact".
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.GetResponse;
import foc.contracts.events.EventContracts;
import foc.contracts.events.EventTypeRegistry;
import foc.contracts.events.OrderCollected;
import foc.contracts.events.OrderCompleted;
import foc.contracts.events.OrderEvent;
import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import foc.notification.service.EventProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * processor stores one row per party, duplicates are absorbed, and
 * keys outside {@code order.#} never reach the queue.
 *
 * <p>Failure paths (issue #65, D6/D7): a transient processing failure
 * recovers through the retry queue (F2.2); a persistent one
 * dead-letters after the attempt cap, proven by the listener's attempt
 * header on the DLQ message rather than wall-clock timing (F2.3); an
 * unknown event type dead-letters intact on first delivery.
 * Failures are injected by a {@code @Primary} decorator around the
 * real processor, keyed on eventId prefixes ({@code poison-} always
 * throws, {@code flaky-} throws once) — the broker path stays fully
 * real. The test yaml sets a 500 ms retry TTL so cycles complete
 * quickly.
 *
 * <p>The broker container and H2 instance are shared by all methods,
 * so assertions are baseline-relative or eventId-scoped, and each
 * method starts from a purged DLQ. Skips cleanly when Docker is not
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

	@TestConfiguration
	static class FailureInjection {

		/**
		 * Decorates the real processor (by bean name — the concrete
		 * class is package-private in the service layer, D11): every
		 * {@code poison-} event fails, every {@code flaky-} event fails
		 * exactly once, everything else flows through untouched.
		 */
		@Bean
		@Primary
		EventProcessor failureInjectingProcessor(
				@Qualifier("idempotentEventProcessor") EventProcessor delegate) {
			Set<String> alreadyFailed = ConcurrentHashMap.newKeySet();
			return event -> {
				if (event.eventId().startsWith("poison-")) {
					throw new IllegalStateException("injected persistent failure: " + event.eventId());
				}
				if (event.eventId().startsWith("flaky-") && alreadyFailed.add(event.eventId())) {
					throw new IllegalStateException("injected transient failure: " + event.eventId());
				}
				delegate.process(event);
			};
		}
	}

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationRepository notifications;

	@BeforeEach
	void purgeDeadLetterQueue() {
		rabbitTemplate.execute(channel ->
				channel.queuePurge(RabbitMqTopology.ORDER_EVENTS_DEAD_LETTER_QUEUE));
	}

	@Test
	void consumesDeduplicatesAndAcksThroughRealBroker() throws IOException {
		long baseline = notifications.count();
		byte[] fixture = canonicalFixtureBytes();

		// Canonical order-accepted fixture (two parties), as raw JSON
		// under its canonical routing key.
		publishRawJson(fixture, "order.accepted");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(notifications.count()).isEqualTo(baseline + 2));

		List<Notification> rows = notifications.findAll().stream()
				.filter(row -> "order.accepted".equals(row.getEventType()))
				.toList();
		assertThat(rows).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder("usr-req-1001", "usr-cou-2002");

		// Duplicate delivery, then a sentinel event: the queue is FIFO, so
		// once the sentinel's row exists the duplicate has been fully
		// processed — and must have produced nothing.
		publishRawJson(fixture, "order.accepted");
		publishEvent(collected("e-int-3"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-int-3")).isEqualTo(1));
		assertThat(notifications.count()).isEqualTo(baseline + 3);

		// Topic routing: a key outside order.# never reaches this queue —
		// the body is a perfectly valid order event, so a row for it would
		// prove a routing leak, not a conversion failure.
		publishRawJson(validBodyWithEventId("e-int-credit"), "credit.granted");

		// Unknown body eventType under a binding-matching key: fatal
		// conversion failure, rejected without requeue on first delivery
		// and dead-lettered straight to the DLQ (D7) — no rows, queue not
		// wedged, message kept intact for replay.
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

		// The dead-lettered copy is replayable: body intact, and x-death
		// records the rejection from the work queue under the original
		// routing key.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount(RabbitMqTopology.ORDER_EVENTS_DEAD_LETTER_QUEUE))
						.isEqualTo(1));
		GetResponse deadLetter = getFromDeadLetterQueue();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.contains("order.refunded").contains("e-int-unknown");
		Map<String, ?> workQueueDeath = xDeathEntry(deadLetter,
				RabbitMqTopology.ORDER_EVENTS_QUEUE, "rejected");
		assertThat(String.valueOf(workQueueDeath.get("routing-keys"))).contains("order.refunded");

		// Everything settled: work and retry queues drain to zero.
		awaitDrained();
	}

	@Test
	void transientFailureRecoversThroughRetryQueue() {
		publishEvent(collected("flaky-e-1"));

		// The row can only exist after a full park-and-return cycle: the
		// decorator failed the first delivery, so success proves the
		// message survived the retry queue (F2.2).
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("flaky-e-1")).isEqualTo(1));

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.ORDER_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void persistentFailureDeadLettersAfterMaxAttempts() {
		publishEvent(collected("poison-e-1"));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount(RabbitMqTopology.ORDER_EVENTS_DEAD_LETTER_QUEUE))
						.isEqualTo(1));
		assertThat(countByEventId("poison-e-1")).isZero();

		// Attempt evidence, stronger than any wall-clock assertion: the
		// listener's attempt header shows two republishes preceded the
		// final delivery (= the configured 3 attempts), and the broker's
		// x-death carries the retry queue's expired entry, proving the
		// park-and-return cycles really ran through the broker (its count
		// stays 1 — RabbitMQ 4 resets it on client republish, which is
		// exactly why the listener stamps its own header). The body stays
		// replayable.
		GetResponse deadLetter = getFromDeadLetterQueue();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.contains("poison-e-1").contains("order.collected");
		Number attemptsStamped = (Number) deadLetter.getProps().getHeaders()
				.get(DomainEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(attemptsStamped.longValue()).isEqualTo(2);
		xDeathEntry(deadLetter, RabbitMqTopology.ORDER_EVENTS_RETRY_QUEUE, "expired");

		awaitDrained();
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
		return new OrderCollected(eventId, Instant.parse("2026-09-19T09:00:00Z"),
				"order-service", "c-int", List.of("usr-req-1001"), "ord-20260919-0042",
				"usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "integration");
	}

	private static OrderCompleted completed(String eventId) {
		return new OrderCompleted(eventId, Instant.parse("2026-09-19T09:05:00Z"),
				"order-service", "c-int", List.of("usr-req-1001"), "ord-20260919-0042",
				"usr-req-1001", "usr-cou-2002", "Techno Edge", "COM3-01-19", "integration");
	}

	private static byte[] validBodyWithEventId(String eventId) {
		return ("""
				{"eventType": "order.created", "eventId": "%s",
				 "occurredAt": "2026-09-19T09:00:00Z",
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

	private long readyMessageCount(String queue) {
		Integer count = rabbitTemplate.execute(channel ->
				channel.queueDeclarePassive(queue).getMessageCount());
		return count == null ? -1 : count;
	}

	/** Every in-flight message settled: work and retry queues at zero. */
	private void awaitDrained() {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(readyMessageCount(RabbitMqTopology.ORDER_EVENTS_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.ORDER_EVENTS_RETRY_QUEUE)).isZero();
		});
	}

	private GetResponse getFromDeadLetterQueue() {
		GetResponse response = rabbitTemplate.execute(channel ->
				channel.basicGet(RabbitMqTopology.ORDER_EVENTS_DEAD_LETTER_QUEUE, true));
		assertThat(response).isNotNull();
		return response;
	}

	/**
	 * The broker's x-death header entry for one queue/reason pair —
	 * values arrive as client types (LongString, Long), so callers
	 * compare via String.valueOf/Number.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, ?> xDeathEntry(GetResponse message, String queue, String reason) {
		List<Map<String, ?>> deaths =
				(List<Map<String, ?>>) message.getProps().getHeaders().get("x-death");
		assertThat(deaths).isNotNull();
		return deaths.stream()
				.filter(death -> queue.equals(String.valueOf(death.get("queue")))
						&& reason.equals(String.valueOf(death.get("reason"))))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"no x-death entry for " + queue + "/" + reason + " in " + deaths));
	}
}
