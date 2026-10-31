/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30.
 * Scope: issue #255's broker integration test, mirroring
 * RequestEventsRabbitMqIntegrationTest for the rating trio: the
 * canonical rating.submitted fixture stores one row for the ratee
 * only, is acked and deduped on redelivery; the exact-key binding
 * leaks nothing across exchanges; failure paths run through the
 * RATING trio's retry queue and DLQ (regression guard on the
 * listener's previously hard-coded request retry queue). Reuses the
 * sibling test's FailureInjection decorator. Recipient scope (ratee
 * only) decided on #44.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.GetResponse;
import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.rating.RatingEvent;
import foc.contracts.events.rating.RatingSubmitted;
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
 * End-to-end through a real broker for the rating trio (issue #255):
 * the canonical {@code rating.submitted} fixture goes to the
 * {@code rating-events} topic exchange under its canonical key; the
 * listener consumes, the processor stores one row for the
 * <em>ratee only</em> (the contract's {@code parties}, decided on
 * #44), duplicates are absorbed, and the exact-key binding lets
 * nothing leak between the request and rating exchanges.
 *
 * <p>Failure paths prove the listener's per-queue retry derivation: a
 * transient failure recovers through
 * {@code notification-service.rating-events.retry}, and a poison event
 * dead-letters into the rating DLQ with x-death evidence naming the
 * rating retry queue — while the request trio stays empty (the old
 * hard-coded target would have parked it there).
 *
 * <p>Same infrastructure stance as the sibling request test: failures
 * injected by its {@code @Primary} decorator ({@code poison-} always
 * throws, {@code flaky-} throws once), broker container and H2 shared
 * across methods so assertions are eventId-scoped, DLQs purged before
 * each method, skips cleanly without Docker.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=true",
		// Own H2 instance so this context never shares tables with the
		// cached broker-free context used by the other tests.
		"spring.datasource.url=jdbc:h2:mem:notification-rating-it;DB_CLOSE_DELAY=-1"
})
@Testcontainers(disabledWithoutDocker = true)
@Import(RequestEventsRabbitMqIntegrationTest.FailureInjection.class)
class RatingEventsRabbitMqIntegrationTest {

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
			channel.queuePurge(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE);
			channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE);
			return null;
		});
	}

	/** The canonical fixture's own eventId — assertions are scoped to it
	 * (the H2 instance is shared by this class's methods, so absolute
	 * row counts would depend on method order). */
	private static final String FIXTURE_EVENT_ID = "9b2f6b3c-1e9d-4d55-8f6a-0c2f4a7d9e32";

	@Test
	void storesFixtureForRateeOnlyAndDeduplicates() throws IOException {
		byte[] fixture = fixtureBytes("rating.submitted");

		// Canonical fixture as raw JSON under its canonical routing key:
		// exactly one row, for the ratee — the rater knows what they did
		// (recipient scope decided on #44).
		publishRawJson(fixture, "rating.submitted");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(rowsByEventId(FIXTURE_EVENT_ID)).hasSize(1));
		Notification stored = rowsByEventId(FIXTURE_EVENT_ID).getFirst();
		assertThat(stored.getRecipientId()).isEqualTo("usr-cou-2002");
		assertThat(stored.getEventType()).isEqualTo("rating.submitted");

		// The stored payload is all the web's template can read (D32: IDs
		// only, names hydrated at read time), so the business fields must
		// land in it — metadata stripped.
		assertThat(payloadOf(stored))
				.containsEntry("ratingId", "rat-20260919-0001")
				.containsEntry("requestId", "req-20260919-0042")
				.containsEntry("raterId", "usr-req-1001")
				.containsEntry("rateeId", "usr-cou-2002")
				.containsEntry("stars", 5)
				.doesNotContainKeys("eventId", "eventType", "parties");

		// Duplicate delivery, then a sentinel: the queue is FIFO, so once
		// the sentinel's row exists the duplicate has been fully processed —
		// and must have produced nothing.
		publishRawJson(fixture, "rating.submitted");
		publishEvent(rating("e-rat-sentinel-1"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-rat-sentinel-1")).isEqualTo(1));
		assertThat(rowsByEventId(FIXTURE_EVENT_ID)).hasSize(1);

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	/**
	 * The rating work queue binds the exact {@code rating.submitted} key
	 * and the request queue binds {@code request.#}: a rating body on the
	 * request exchange and a request key on the rating exchange are both
	 * unroutable — a row for either would prove a binding leak, not a
	 * conversion failure (both bodies are perfectly valid events).
	 */
	@Test
	void exactKeyBindingLeaksNothingAcrossExchanges() throws IOException {
		byte[] ratingFixture = fixtureBytes("rating.submitted");
		rabbitTemplate.send(EventContracts.REQUEST_EVENTS_EXCHANGE, "rating.submitted",
				jsonMessage(withEventId(ratingFixture, "e-rat-wrong-exchange")));
		rabbitTemplate.send(EventContracts.RATING_EVENTS_EXCHANGE, "request.accepted",
				jsonMessage(withEventId(fixtureBytes("request.accepted"), "e-req-wrong-exchange")));

		publishEvent(rating("e-rat-sentinel-2"));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("e-rat-sentinel-2")).isEqualTo(1));
		assertThat(countByEventId("e-rat-wrong-exchange")).isZero();
		assertThat(countByEventId("e-req-wrong-exchange")).isZero();

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE)).isZero();
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void transientFailureRecoversThroughRatingRetryQueue() {
		publishEvent(rating("flaky-rat-1"));

		// The row can only exist after a full park-and-return cycle: the
		// decorator failed the first delivery, so success proves the
		// message survived the rating retry queue (F2.2).
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(countByEventId("flaky-rat-1")).isEqualTo(1));

		awaitDrained();
		assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE)).isZero();
	}

	@Test
	void persistentFailureDeadLettersThroughRatingTrio() {
		publishEvent(rating("poison-rat-1"));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE))
						.isEqualTo(1));
		assertThat(countByEventId("poison-rat-1")).isZero();

		// Attempt evidence as in the sibling test, plus the point of this
		// trio: x-death names the RATING retry queue, so the park-and-return
		// cycles ran through this queue's own trio — and the request trio
		// saw nothing (the listener's old hard-coded retry target would
		// have parked the event there).
		GetResponse deadLetter = getFromDeadLetterQueue();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.contains("poison-rat-1").contains("rating.submitted");
		Number attemptsStamped = (Number) deadLetter.getProps().getHeaders()
				.get(DomainEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(attemptsStamped.longValue()).isEqualTo(2);
		xDeathEntry(deadLetter, RabbitMqTopology.RATING_EVENTS_RETRY_QUEUE, "expired");
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)).isZero();

		awaitDrained();
	}

	/** The canonical fixture shipped in the foc-contracts jar for one event type. */
	private static byte[] fixtureBytes(String eventType) throws IOException {
		String path = "/contracts/" + eventType.replace('.', '-') + ".example.json";
		try (InputStream fixture = RatingEventsRabbitMqIntegrationTest.class.getResourceAsStream(path)) {
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
		rabbitTemplate.send(EventContracts.RATING_EVENTS_EXCHANGE, routingKey, jsonMessage(body));
	}

	/** Producer convention (D18): routing key from the registry, by class. */
	private void publishEvent(RatingEvent event) {
		rabbitTemplate.convertAndSend(EventContracts.RATING_EVENTS_EXCHANGE,
				EventTypeRegistry.routingKeyFor(event.getClass()), event);
	}

	/** Parties = the ratee alone, as rating-service publishes it (#44). */
	private static RatingSubmitted rating(String eventId) {
		return new RatingSubmitted(eventId, Instant.parse("2026-09-19T09:30:00Z"),
				"rating-service", "c-int", List.of("usr-cou-2002"), "rat-20260919-0001",
				"req-20260919-0042", "usr-req-1001", "usr-cou-2002", 4);
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
			assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.RATING_EVENTS_RETRY_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isZero();
			assertThat(readyMessageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		});
	}

	private GetResponse getFromDeadLetterQueue() {
		GetResponse response = rabbitTemplate.execute(channel ->
				channel.basicGet(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE, true));
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
