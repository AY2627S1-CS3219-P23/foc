/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: the reply path through a real broker and a real Postgres, from
 * the outbox row to the message on a queue bound to credit-events
 * (docs/credit-service.md D10 and its two failure rows).
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import foc.contracts.events.core.EventContracts;
import foc.contracts.events.credit.CreditReservationRejected;
import foc.contracts.events.credit.CreditReserved;
import foc.credit.PostgresTestContainer;
import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.service.ReplyOutbox;
import tools.jackson.databind.json.JsonMapper;

/**
 * The publisher polls on its own schedule here (every 200 ms), so the
 * tests only write outbox rows and wait for what it does with them. The
 * Order Service's queue is stood in for by a queue each test binds, or
 * leaves unbound, itself.
 */
@SpringBootTest(properties = {
		"credit.rabbitmq.provision-on-startup=true",
		"credit.outbox.publisher.enabled=true",
		"credit.outbox.publisher.poll-interval-ms=200"
})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class OutboxPublisherIntegrationTest extends PostgresTestContainer {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final String ORDER_QUEUE = "test.order-service.credit-events";

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Autowired
	private ReplyOutbox outbox;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void noQueueBoundAndAnEmptyOutbox() {
		amqpAdmin.deleteQueue(ORDER_QUEUE);
		outboxEvents.deleteAll();
	}

	@Test
	void aReplyIsPublishedAsItsEventAndMarkedSent() {
		bindOrderQueue();
		CreditReserved reply = new CreditReserved(UUID.randomUUID().toString(), Instant.now(), "credit-service",
				"corr-1", List.of(), "r-1", "u-req", 3);

		transaction.executeWithoutResult(status -> outbox.enqueue(reply));

		Message message = rabbitTemplate.receive(ORDER_QUEUE, TIMEOUT.toMillis());
		assertThat(message).isNotNull();
		assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("credit.reserved");
		assertThat(message.getMessageProperties().getReceivedExchange())
				.isEqualTo(EventContracts.CREDIT_EVENTS_EXCHANGE);
		// read back the way a consumer does: by the body's eventType
		assertThat(new DomainEventMessageConverter(jsonMapper).fromMessage(message)).isEqualTo(reply);
		awaitSent(reply.eventId());
	}

	@Test
	void aReplyPublishedWithNoQueueBoundStaysUnsentUntilOneIs(CapturedOutput output) {
		CreditReservationRejected reply = new CreditReservationRejected(UUID.randomUUID().toString(), Instant.now(),
				"credit-service", "corr-2", List.of(), "r-2", "u-req", 9, "insufficient available credits");

		transaction.executeWithoutResult(status -> outbox.enqueue(reply));

		// the broker confirmed it and returned it: the publisher says so
		// and leaves the row unsent
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(output.getOut()).contains(
				"Reply " + reply.eventId() + " (credit.reservation-rejected) stays unsent: returned as unroutable"));
		assertThat(stored(reply.eventId()).getSentAt()).isNull();

		// the Order Service starts: a later poll delivers the same reply
		bindOrderQueue();

		Message message = rabbitTemplate.receive(ORDER_QUEUE, TIMEOUT.toMillis());
		assertThat(message).isNotNull();
		assertThat(new DomainEventMessageConverter(jsonMapper).fromMessage(message)).isEqualTo(reply);
		awaitSent(reply.eventId());
	}

	// The publisher keeps nothing in memory between polls, so a reply
	// left unsent by a run that stopped is, to the next run, a row in the
	// table: written here directly, as that earlier run committed it.
	@Test
	void anUnsentReplyLeftByAnEarlierRunIsPublished() {
		bindOrderQueue();
		UUID eventId = UUID.randomUUID();
		String payload = "{\"eventId\":\"" + eventId + "\",\"occurredAt\":\"2026-10-12T08:00:00Z\","
				+ "\"producer\":\"credit-service\",\"correlationId\":\"corr-3\",\"parties\":[],"
				+ "\"requestId\":\"r-3\",\"requesterId\":\"u-req\",\"amount\":4,\"eventType\":\"credit.reserved\"}";
		outboxEvents.save(new OutboxEvent(eventId, "credit.reserved", payload, Instant.now().minusSeconds(3600)));

		Message message = rabbitTemplate.receive(ORDER_QUEUE, TIMEOUT.toMillis());
		assertThat(message).isNotNull();
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
		awaitSent(eventId.toString());
	}

	@Test
	void aSentReplyIsNotPublishedAgain() {
		bindOrderQueue();
		CreditReserved reply = new CreditReserved(UUID.randomUUID().toString(), Instant.now(), "credit-service",
				"corr-4", List.of(), "r-4", "u-req", 1);
		transaction.executeWithoutResult(status -> outbox.enqueue(reply));
		assertThat(rabbitTemplate.receive(ORDER_QUEUE, TIMEOUT.toMillis())).isNotNull();
		awaitSent(reply.eventId());

		// several more polls pass (200 ms apart) and none republishes it
		assertThat(rabbitTemplate.receive(ORDER_QUEUE, 1500)).isNull();
	}

	private void bindOrderQueue() {
		// durable, as the Order Service's will be (this broker refuses a
		// transient queue that is not exclusive)
		amqpAdmin.declareQueue(new Queue(ORDER_QUEUE, true, false, false));
		amqpAdmin.declareBinding(new Binding(ORDER_QUEUE, Binding.DestinationType.QUEUE,
				EventContracts.CREDIT_EVENTS_EXCHANGE, "credit.#", null));
	}

	private void awaitSent(String eventId) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(stored(eventId).getSentAt()).isNotNull());
	}

	private OutboxEvent stored(String eventId) {
		return outboxEvents.findById(UUID.fromString(eventId)).orElseThrow();
	}
}
