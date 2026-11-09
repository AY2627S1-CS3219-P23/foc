/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-09.
 * Scope: Credit NFR1.1 (PR #313) review follow-up: broker integration
 * test for the credit trio, mirroring RatingEventsRabbitMqIntegrationTest.
 * The canonical credit.transferred fixture stores one row per party and
 * is deduped on redelivery; credit.reserved and
 * credit.reservation-rejected (the saga replies, order-service's) never
 * reach this service's credit queue; failure paths run through the
 * CREDIT trio's retry queue and DLQ. Reuses the request test's
 * FailureInjection decorator.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.GetResponse;
import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.credit.CreditEvent;
import foc.contracts.events.credit.CreditTransferred;
import foc.notification.entity.Notification;
import foc.notification.repository.NotificationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end through a real broker for the credit trio (Credit NFR1.1,
 * PR #313): the canonical {@code credit.transferred} fixture goes to the
 * {@code credit-events} topic exchange under its canonical key; the
 * listener consumes, the processor stores one row for the requester and
 * one for the courier, and a redelivery is absorbed.
 *
 * <p>The exchange also carries the saga's replies
 * ({@code credit.reserved}, {@code credit.reservation-rejected}), which
 * belong to order-service and have no recipient rule here. The exact-key
 * binding is all that keeps them out of this queue: were it widened
 * (say to {@code credit.#}), every reservation would fail recipient
 * resolution and park in the credit DLQ. The leak test pins that down.
 *
 * <p>Failure paths prove the listener's per-queue retry derivation for
 * this queue: a transient failure recovers through
 * {@code notification-service.credit-events.retry}, and a poison event
 * dead-letters into the credit DLQ with x-death evidence naming the
 * credit retry queue, while the request trio stays empty.
 *
 * <p>Same infrastructure stance as the sibling tests: failures injected
 * by the request test's {@code @Primary} decorator ({@code poison-}
 * always throws, {@code flaky-} throws once), broker container and H2
 * shared across methods so assertions are eventId-scoped, DLQs purged
 * before each method, skips cleanly without Docker.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=true",
		// Own H2 instance so this context never shares tables with the
		// cached broker-free context used by the other tests.
		"spring.datasource.url=jdbc:h2:mem:notification-credit-it;DB_CLOSE_DELAY=-1"
})
@Testcontainers(disabledWithoutDocker = true)
@Import(RequestEventsRabbitMqIntegrationTest.FailureInjection.class)
class CreditEventsRabbitMqIntegrationTest {

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void purgeDeadLetterQueues() {
		rabbitTemplate.execute(channel -> {
			channel.queuePurge(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE);
			channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE);
			return null;
		});
	}

	/** The canonical fixture's own eventId: assertions are scoped to it
	 * (the H2 instance is shared by this class's methods, so absolute
	 * row counts would depend on method order). */
	private static final String FIXTURE_EVENT_ID = "9b2f6b3c-1e9d-4d55-8f6a-0c2f4a7d9e40";

	private static final String REQUESTER = "usr-req-1001";
	private static final String COURIER = "usr-cou-2002";

	@Test
	void storesFixtureForBothPartiesAndDeduplicates() throws IOException {
		byte[] fixture = fixtureBytes("credit.transferred");

		// Canonical fixture as raw JSON under its canonical routing key:
		// one row each for the requester and the courier.
		publishRawJson(fixture, "credit.transferred");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(rowsByEventId(FIXTURE_EVENT_ID)).hasSize(2));
		List<Notification> stored = rowsByEventId(FIXTURE_EVENT_ID);
		assertThat(stored).extracting(Notification::getRecipientId)
				.containsExactlyInAnyOrder(REQUESTER, COURIER);
		assertThat(stored).extracting(Notification::getEventType)
				.containsOnly("credit.transferred");

		// The stored payload is what the web's copy reads (D32: IDs only),
		// so the business fields must land in it, metadata stripped.
		assertThat(payloadOf(stored.getFirst()))
				.containsEntry("requestId", "req-20260919-0042")
				.containsEntry("requesterId", REQUESTER)
				.containsEntry("courierId", COURIER)
				.containsEntry("amount", 2)
				.doesNotContainKeys("eventId", "eventType");

		// Duplicate delivery, then a sentinel: the queue is FIFO, so once
		// the sentinel's rows exist the duplicate has been fully processed
		// and must have produced nothing.
		publishRawJson(fixture, "credit.transferred");
		publishEvent(transferred("e-cre-sentinel-1"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-cre-sentinel-1")).isEqualTo(2));
		assertThat(rowsByEventId(FIXTURE_EVENT_ID)).hasSize(2);

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	/**
	 * The saga replies share the credit-events exchange but are not bound
	 * to this queue: both bodies are valid events, so a row would prove a
	 * binding leak, and a dead letter would prove they reached the queue
	 * and failed recipient resolution. A credit body on the request
	 * exchange must not route either.
	 */
	@Test
	void sagaRepliesNeverReachCreditQueue() throws IOException {
		rabbitTemplate.send(EventContracts.CREDIT_EVENTS_EXCHANGE, "credit.reserved",
				jsonMessage(withEventId(fixtureBytes("credit.reserved"), "e-cre-reserved")));
		rabbitTemplate.send(EventContracts.CREDIT_EVENTS_EXCHANGE, "credit.reservation-rejected",
				jsonMessage(withEventId(fixtureBytes("credit.reservation-rejected"),
						"e-cre-rejected")));
		rabbitTemplate.send(EventContracts.REQUEST_EVENTS_EXCHANGE, "credit.transferred",
				jsonMessage(withEventId(fixtureBytes("credit.transferred"),
						"e-cre-wrong-exchange")));

		publishEvent(transferred("e-cre-sentinel-2"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-cre-sentinel-2")).isEqualTo(2));
		assertThat(countByEventId("e-cre-reserved")).isZero();
		assertThat(countByEventId("e-cre-rejected")).isZero();
		assertThat(countByEventId("e-cre-wrong-exchange")).isZero();

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE)).isZero();
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void transientFailureRecoversThroughCreditRetryQueue() {
		publishEvent(transferred("flaky-cre-1"));

		// The rows can only exist after a full park-and-return cycle: the
		// decorator failed the first delivery, so success proves the
		// message survived the credit retry queue.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("flaky-cre-1")).isEqualTo(2));

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void persistentFailureDeadLettersThroughCreditTrio() {
		publishEvent(transferred("poison-cre-1"));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE))
						.isEqualTo(1));
		assertThat(countByEventId("poison-cre-1")).isZero();

		// x-death names the CREDIT retry queue, so the park-and-return
		// cycles ran through this queue's own trio, and the request trio
		// saw nothing.
		GetResponse deadLetter = getFromDeadLetterQueue();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.contains("poison-cre-1").contains("credit.transferred");
		Number attemptsStamped = (Number) deadLetter.getProps().getHeaders()
				.get(DomainEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(attemptsStamped.longValue()).isEqualTo(2);
		xDeathEntry(deadLetter, RabbitMqTopology.CREDIT_EVENTS_RETRY_QUEUE, "expired");
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();

		awaitDrained();
	}

	/** The canonical fixture shipped in the foc-contracts jar for one event type. */
	private static byte[] fixtureBytes(String eventType) throws IOException {
		String path = "/contracts/" + eventType.replace('.', '-') + ".example.json";
		try (InputStream fixture = CreditEventsRabbitMqIntegrationTest.class.getResourceAsStream(path)) {
			assertThat(fixture).as("fixture %s", path).isNotNull();
			return fixture.readAllBytes();
		}
	}

	/** The fixture body with its eventId swapped, so dedupe cannot hide a leak. */
	private byte[] withEventId(byte[] fixture, String eventId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> body = objectMapper.readValue(fixture, Map.class);
		body.put("eventId", eventId);
		return objectMapper.writeValueAsBytes(body);
	}

	private static Message jsonMessage(byte[] body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		return new Message(body, properties);
	}

	private void publishRawJson(byte[] body, String routingKey) {
		rabbitTemplate.send(EventContracts.CREDIT_EVENTS_EXCHANGE, routingKey, jsonMessage(body));
	}

	/** Producer convention (D18): routing key from the registry, by class. */
	private void publishEvent(CreditEvent event) {
		rabbitTemplate.convertAndSend(EventContracts.CREDIT_EVENTS_EXCHANGE,
				EventTypeRegistry.routingKeyFor(event.getClass()), event);
	}

	/** As credit-service publishes it on a settled transfer; tells both parties. */
	private static CreditTransferred transferred(String eventId) {
		return new CreditTransferred(eventId, Instant.parse("2026-09-19T09:30:00Z"),
				"credit-service", "c-int", "req-20260919-0042", REQUESTER, COURIER, 2);
	}

	private List<Notification> rowsByEventId(String eventId) {
		return notifications.findAll().stream()
				.filter(row -> eventId.equals(row.getEventId()))
				.toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> payloadOf(Notification row) {
		return objectMapper.readValue(row.getPayload(), Map.class);
	}

	private long countByEventId(String eventId) {
		return rowsByEventId(eventId).size();
	}

	private long readyMessageCount(String queue) {
		Integer count = rabbitTemplate.execute(channel ->
				channel.queueDeclarePassive(queue).getMessageCount());
		return count == null ? -1 : count;
	}

	/** Every in-flight message settled: both trios' work and retry queues at zero. */
	private void awaitDrained() {
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.CREDIT_EVENTS_RETRY_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		});
	}

	private GetResponse getFromDeadLetterQueue() {
		GetResponse response = rabbitTemplate.execute(channel ->
				channel.basicGet(RabbitMqTopology.CREDIT_EVENTS_DEAD_LETTER_QUEUE, true));
		assertThat(response).isNotNull();
		return response;
	}

	/**
	 * The broker's x-death header entry for one queue/reason pair; values
	 * arrive as client types (LongString, Long), so callers compare via
	 * String.valueOf/Number.
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
