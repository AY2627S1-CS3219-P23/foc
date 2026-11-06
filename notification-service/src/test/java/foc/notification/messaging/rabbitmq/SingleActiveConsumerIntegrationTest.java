/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06.
 * Scope: issue #48 (DP10): single active consumer on the request work queue
 * (team decision, Leong Wei Zhi via options Q&A) on a real broker: with two
 * replicas subscribed, every delivery goes to the first while the second
 * stays idle, and the second takes over once the first goes away.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import foc.contracts.events.core.EventContracts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * No Spring context: the production {@link RabbitMqTopology} declarations
 * are declared on a throwaway broker, and two plain AMQP connections stand
 * in for two replicas' listener containers.
 */
@Testcontainers(disabledWithoutDocker = true)
class SingleActiveConsumerIntegrationTest {

	@Container
	static RabbitMQContainer rabbitMq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	@Test
	void onlyTheFirstReplicaConsumesUntilItGoes() throws Exception {
		declare(new RabbitMqTopology().requestEventsTopology(500));
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(rabbitMq.getHost());
		factory.setPort(rabbitMq.getAmqpPort());
		factory.setUsername(rabbitMq.getAdminUsername());
		factory.setPassword(rabbitMq.getAdminPassword());

		Connection replicaA = factory.newConnection("replica-a");
		try (Connection replicaB = factory.newConnection("replica-b");
				Connection publisherConnection = factory.newConnection("publisher")) {
			List<String> seenByA = new CopyOnWriteArrayList<>();
			List<String> seenByB = new CopyOnWriteArrayList<>();
			subscribe(replicaA.createChannel(), seenByA);
			subscribe(replicaB.createChannel(), seenByB);
			Channel publisher = publisherConnection.createChannel();

			for (int i = 1; i <= 5; i++) {
				publish(publisher, "m" + i);
			}
			await().atMost(Duration.ofSeconds(10)).until(() -> seenByA.size() == 5);
			assertThat(seenByA).containsExactly("m1", "m2", "m3", "m4", "m5");
			assertThat(seenByB).as("the second replica stays inactive").isEmpty();

			// the active replica goes away: the broker hands over to the next
			replicaA.close();
			publish(publisher, "m6");
			await().atMost(Duration.ofSeconds(10)).until(() -> seenByB.size() == 1);
			assertThat(seenByB).containsExactly("m6");
			assertThat(seenByA).hasSize(5);
		} finally {
			if (replicaA.isOpen()) {
				replicaA.close();
			}
		}
	}

	private static void declare(Declarables topology) {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitMq.getHost(),
				rabbitMq.getAmqpPort());
		connectionFactory.setUsername(rabbitMq.getAdminUsername());
		connectionFactory.setPassword(rabbitMq.getAdminPassword());
		try {
			RabbitAdmin admin = new RabbitAdmin(connectionFactory);
			topology.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
			topology.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
			topology.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
		} finally {
			connectionFactory.destroy();
		}
	}

	private static String subscribe(Channel channel, List<String> seen) throws Exception {
		return channel.basicConsume(RabbitMqTopology.REQUEST_EVENTS_QUEUE, false, new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws java.io.IOException {
				seen.add(new String(body, StandardCharsets.UTF_8));
				getChannel().basicAck(envelope.getDeliveryTag(), false);
			}
		});
	}

	private static void publish(Channel channel, String body) throws Exception {
		channel.basicPublish(EventContracts.REQUEST_EVENTS_EXCHANGE, "request.created", null,
				body.getBytes(StandardCharsets.UTF_8));
	}
}
