/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: the topology declared against a real broker (the author's
 * instruction), following notification-service's
 * RequestEventsRabbitMqIntegrationTest: Testcontainers RabbitMQ, startup
 * provisioning on, raw fixture JSON published under its routing key.
 * PR #168 review (Leong Wei Zhi): the unbound keys are published before
 * the bound ones, and the fixture is read as its literal bytes.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
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

import com.rabbitmq.client.GetResponse;

import foc.contracts.events.core.EventContracts;
import foc.credit.PostgresTestContainer;

/**
 * The service starts against a real broker with provisioning on, so a
 * declaration the broker refuses fails this context, and the declared
 * queues then behave as docs/credit-service.md D11 says: the four bound
 * keys reach the work queue and no other, the retry queue returns an
 * event to it after the delay, and a rejected event lands in the DLQ.
 *
 * <p>No listener exists yet, so the test reads the queues itself. The
 * container is shared by the methods; each starts from purged queues.
 */
@SpringBootTest(properties = {
		"credit.rabbitmq.provision-on-startup=true",
		// short, so the retry leg is seen without waiting out the default
		"credit.rabbitmq.retry.ttl-ms=500"
})
@Testcontainers(disabledWithoutDocker = true)
class RabbitMqTopologyIntegrationTest extends PostgresTestContainer {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@BeforeEach
	void purgeQueues() {
		rabbitTemplate.execute(channel -> {
			channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_QUEUE);
			channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE);
			channel.queuePurge(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE);
			return null;
		});
	}

	@Test
	void aSubmittedRequestReachesTheWorkQueue() throws IOException {
		byte[] fixture = fixture("request-submitted");

		publish(EventContracts.REQUEST_EVENTS_EXCHANGE, "request.submitted", fixture);

		awaitReady(RabbitMqTopology.REQUEST_EVENTS_QUEUE, 1);
		assertThat(get(RabbitMqTopology.REQUEST_EVENTS_QUEUE, true).getBody()).isEqualTo(fixture);
	}

	@Test
	void onlyTheFourConsumedKeysAreBound() throws IOException {
		byte[] body = fixture("request-submitted");

		// Routing is by key alone, so one body serves for every key. The
		// three unbound keys go first, on one channel: the broker routes a
		// channel's publishes in order, so once the four bound ones are in
		// the queue, a wrongly bound key has already added to the count.
		rabbitTemplate.invoke(sameChannel -> {
			for (String key : new String[] { "request.created", "request.accepted", "request.rejected",
					"request.submitted", "request.completed", "request.cancelled", "request.expired" }) {
				sameChannel.send(EventContracts.REQUEST_EVENTS_EXCHANGE, key, json(body));
			}
			return null;
		});

		await().atMost(TIMEOUT).untilAsserted(
				() -> assertThat(readyCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isGreaterThanOrEqualTo(4));
		assertThat(readyCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isEqualTo(4);
	}

	@Test
	void theRetryQueueReturnsAnEventToTheWorkQueue() throws IOException {
		byte[] fixture = fixture("request-submitted");

		// as the listener will park a failed event: by queue name, on the
		// default exchange
		publish("", RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, fixture);

		awaitReady(RabbitMqTopology.REQUEST_EVENTS_QUEUE, 1);
		assertThat(readyCount(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE)).isZero();
		assertThat(get(RabbitMqTopology.REQUEST_EVENTS_QUEUE, true).getBody()).isEqualTo(fixture);
	}

	@Test
	void aRejectedEventLandsInTheDeadLetterQueue() throws IOException {
		byte[] fixture = fixture("request-submitted");
		publish(EventContracts.REQUEST_EVENTS_EXCHANGE, "request.submitted", fixture);
		awaitReady(RabbitMqTopology.REQUEST_EVENTS_QUEUE, 1);

		// nack without requeue, as the listener will for INVALID_STATE
		rabbitTemplate.execute(channel -> {
			GetResponse delivery = channel.basicGet(RabbitMqTopology.REQUEST_EVENTS_QUEUE, false);
			channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
			return null;
		});

		awaitReady(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE, 1);
		assertThat(readyCount(RabbitMqTopology.REQUEST_EVENTS_QUEUE)).isZero();
		assertThat(get(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE, true).getBody()).isEqualTo(fixture);
	}

	@Test
	void theCreditEventsExchangeExists() {
		// passive: fails the channel if the exchange is not there
		rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(EventContracts.CREDIT_EVENTS_EXCHANGE));
	}

	/** Raw JSON with no type headers: the wire contract as the Order Service publishes it. */
	private void publish(String exchange, String routingKey, byte[] body) {
		rabbitTemplate.send(exchange, routingKey, json(body));
	}

	private static Message json(byte[] body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		return new Message(body, properties);
	}

	private void awaitReady(String queue, long expected) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(readyCount(queue)).isEqualTo(expected));
	}

	private long readyCount(String queue) {
		Integer count = rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount());
		return count == null ? -1 : count;
	}

	private GetResponse get(String queue, boolean autoAck) {
		GetResponse response = rabbitTemplate.execute(channel -> channel.basicGet(queue, autoAck));
		assertThat(response).isNotNull();
		return response;
	}

	private static byte[] fixture(String name) throws IOException {
		try (InputStream in = RabbitMqTopologyIntegrationTest.class
				.getResourceAsStream("/contracts/" + name + ".example.json")) {
			assertThat(in).as("fixture " + name).isNotNull();
			return in.readAllBytes();
		}
	}
}
