/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-14, issue #171.
 * Scope: the request event listener through a real RabbitMQ and
 * Postgres (Testcontainers), following notification-service's
 * RequestEventsRabbitMqIntegrationTest: a reservation applied with its
 * reply in the outbox, a duplicate delivery changing nothing, a
 * completion that overtook its reservation transferred on a retry, a
 * completion with no reservation retried then dead-lettered, a conflict
 * dead-lettered at once, and a fractional reward dead-lettered on first
 * delivery.
 * Same day, on review: the cap asserted as total attempts, and a
 * submission with no requester dead-lettered on first delivery.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.rabbitmq.client.GetResponse;

import foc.contracts.events.core.EventContracts;
import foc.contracts.events.core.EventTypeRegistry;
import foc.contracts.events.request.RequestCancelled;
import foc.contracts.events.request.RequestCompleted;
import foc.contracts.events.request.RequestEvent;
import foc.contracts.events.request.RequestSubmitted;
import foc.credit.PostgresTestContainer;
import foc.credit.entity.CreditAccount;
import foc.credit.entity.ReservationStatus;
import foc.credit.entity.Reserved;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.repository.ReservedRepository;

@SpringBootTest(properties = {
		"credit.rabbitmq.provision-on-startup=true",
		"spring.rabbitmq.listener.simple.auto-startup=true",
		// a short park, so a retried event comes back quickly
		"credit.rabbitmq.retry.ttl-ms=200",
		"credit.rabbitmq.retry.max-attempts=" + RequestEventsListenerIntegrationTest.MAX_ATTEMPTS
})
@Testcontainers(disabledWithoutDocker = true)
class RequestEventsListenerIntegrationTest extends PostgresTestContainer {

	/** Total deliveries of one event, the first included, before it is dead-lettered. */
	static final int MAX_ATTEMPTS = 3;

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ReservedRepository reservations;

	@Autowired
	private CreditAccountRepository accounts;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@BeforeEach
	void emptyDeadLetterQueue() {
		rabbitTemplate.execute(channel ->
				channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE));
	}

	@Test
	void aReservationIsAppliedWithItsReplyAndADuplicateChangesNothing() {
		String requester = user();
		String request = request();
		RequestSubmitted submitted = submitted(request, requester, 2);

		publish(submitted);
		await().atMost(TIMEOUT).until(() -> reservations.existsById(request));
		assertThat(reservations.findById(request)).get()
				.extracting(Reserved::getStatus).isEqualTo(ReservationStatus.HELD);
		assertBalance(requester, 3, 2);
		assertThat(repliesFor(request)).containsExactly("credit.reserved");

		// The same delivery again, then a sentinel: the queue is FIFO and
		// has one consumer, so once the sentinel's record exists the
		// duplicate has been fully processed, and must have changed nothing.
		publish(submitted);
		String sentinel = request();
		publish(submitted(sentinel, user(), 1));
		await().atMost(TIMEOUT).until(() -> reservations.existsById(sentinel));

		assertBalance(requester, 3, 2);
		assertThat(repliesFor(request)).containsExactly("credit.reserved");
		awaitDrained();
		assertThat(deadLettered()).isZero();
	}

	@Test
	void aCompletionThatOvertookItsReservationIsTransferredOnARetry() {
		String requester = user();
		String courier = user();
		String request = request();

		// the completion arrives first: no record, so it goes to the retry
		// queue, and the reservation behind it is applied meanwhile
		publish(completed(request, requester, courier));
		publish(submitted(request, requester, 2));

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(reservations.findById(request)).get()
				.extracting(Reserved::getStatus).isEqualTo(ReservationStatus.TRANSFERRED));
		assertBalance(requester, 3, 0);
		assertBalance(courier, 7, 0);
		awaitDrained();
		assertThat(deadLettered()).isZero();
	}

	@Test
	void aCompletionWithNoReservationIsRetriedThenDeadLettered() {
		String request = request();

		publish(completed(request, user(), user()));

		await().atMost(TIMEOUT).until(() -> deadLettered() == 1);
		GetResponse deadLetter = takeDeadLetter();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8))
				.contains("request.completed").contains(request);
		// the header counts republishes; the first delivery makes one more
		long republishes = ((Number) deadLetter.getProps().getHeaders()
				.get(RequestEventsListener.RETRY_ATTEMPTS_HEADER)).longValue();
		assertThat(republishes + 1).as("total attempts").isEqualTo(MAX_ATTEMPTS);
		assertThat(reservations.existsById(request)).isFalse();
		awaitDrained();
	}

	@Test
	void aCompletionThatConflictsWithTheRecordIsDeadLetteredWithoutARetry() {
		String requester = user();
		String request = request();
		// cancelled with no reservation: an amount-0 RELEASED record
		publish(cancelled(request, requester));
		await().atMost(TIMEOUT).until(() -> reservations.existsById(request));

		publish(completed(request, requester, user()));

		await().atMost(TIMEOUT).until(() -> deadLettered() == 1);
		GetResponse deadLetter = takeDeadLetter();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains("request.completed");
		// dead-lettered on its first delivery: never republished
		assertThat(deadLetter.getProps().getHeaders())
				.doesNotContainKey(RequestEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(reservations.findById(request)).get()
				.extracting(Reserved::getStatus).isEqualTo(ReservationStatus.RELEASED);
		awaitDrained();
	}

	@Test
	void aFractionalRewardIsDeadLetteredOnFirstDelivery() {
		String request = request();
		publishRaw("""
				{"eventType": "request.submitted", "eventId": "%s",
				 "occurredAt": "2026-10-14T08:00:00Z", "producer": "order-service",
				 "correlationId": "c-it", "parties": ["%s"], "requestId": "%s",
				 "requesterId": "%s", "pickupLocation": "Techno Edge",
				 "dropoffLocation": "COM3-01-19", "note": null, "reward": 1.7}
				""".formatted(UUID.randomUUID(), "u-frac", request, "u-frac"), "request.submitted");

		await().atMost(TIMEOUT).until(() -> deadLettered() == 1);
		GetResponse deadLetter = takeDeadLetter();
		assertThat(deadLetter.getProps().getHeaders())
				.doesNotContainKey(RequestEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(reservations.existsById(request)).isFalse();
		awaitDrained();
	}

	@Test
	void aSubmissionWithNoRequesterIsDeadLetteredOnFirstDelivery() {
		String request = request();
		publishRaw("""
				{"eventType": "request.submitted", "eventId": "%s",
				 "occurredAt": "2026-10-14T08:00:00Z", "producer": "order-service",
				 "correlationId": "c-it", "parties": [], "requestId": "%s",
				 "pickupLocation": "Techno Edge", "dropoffLocation": "COM3-01-19",
				 "note": null, "reward": 2}
				""".formatted(UUID.randomUUID(), request), "request.submitted");

		// refused by the converter before the listener sees it: no retries
		await().atMost(TIMEOUT).until(() -> deadLettered() == 1);
		GetResponse deadLetter = takeDeadLetter();
		assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains(request);
		assertThat(deadLetter.getProps().getHeaders())
				.doesNotContainKey(RequestEventsListener.RETRY_ATTEMPTS_HEADER);
		assertThat(reservations.existsById(request)).isFalse();
		awaitDrained();
	}

	private void assertBalance(String userId, int available, int reserved) {
		CreditAccount account = accounts.findById(userId).orElseThrow();
		assertThat(account.getAvailable()).as("available of %s", userId).isEqualTo(available);
		assertThat(account.getReserved()).as("reserved of %s", userId).isEqualTo(reserved);
	}

	private List<String> repliesFor(String request) {
		return outboxEvents.findAll().stream()
				.filter(reply -> reply.getPayload().contains("\"" + request + "\""))
				.map(reply -> reply.getEventType())
				.toList();
	}

	private void publish(RequestEvent event) {
		rabbitTemplate.convertAndSend(EventContracts.REQUEST_EVENTS_EXCHANGE,
				EventTypeRegistry.routingKeyFor(event.getClass()), event);
	}

	private void publishRaw(String body, String routingKey) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		rabbitTemplate.send(EventContracts.REQUEST_EVENTS_EXCHANGE, routingKey,
				new Message(body.getBytes(StandardCharsets.UTF_8), properties));
	}

	private long deadLettered() {
		return messageCount(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE);
	}

	private GetResponse takeDeadLetter() {
		return rabbitTemplate.execute(channel ->
				channel.basicGet(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE, true));
	}

	private long messageCount(String queue) {
		Long count = rabbitTemplate.execute(channel -> channel.messageCount(queue));
		return count == null ? 0 : count;
	}

	// nothing left in flight that could leak into the next test
	private void awaitDrained() {
		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(messageCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isZero();
			assertThat(messageCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		});
	}

	private static String user() {
		return "u-it-" + UUID.randomUUID();
	}

	private static String request() {
		return "r-it-" + UUID.randomUUID();
	}

	private static RequestSubmitted submitted(String request, String requester, int reward) {
		return new RequestSubmitted(UUID.randomUUID().toString(), Instant.now(), "order-service", "c-it",
				List.of(requester), request, requester, "Techno Edge", "COM3-01-19", null, reward);
	}

	private static RequestCompleted completed(String request, String requester, String courier) {
		return new RequestCompleted(UUID.randomUUID().toString(), Instant.now(), "order-service", "c-it",
				List.of(requester, courier), request, requester, courier, "Techno Edge", "COM3-01-19", null);
	}

	private static RequestCancelled cancelled(String request, String requester) {
		return new RequestCancelled(UUID.randomUUID().toString(), Instant.now(), "order-service", "c-it",
				List.of(requester), request, requester, null, "Techno Edge", "COM3-01-19", null);
	}
}
