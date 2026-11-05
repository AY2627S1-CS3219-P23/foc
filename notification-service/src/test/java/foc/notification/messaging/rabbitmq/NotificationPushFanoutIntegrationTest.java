/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — broker integration test:
 * one stored notification's push reaches two replicas' queues on the
 * fanout exchange, each exactly once.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import foc.contracts.events.request.CourierArrived;
import foc.notification.messaging.stomp.LocalUserPushDelivery;
import foc.notification.messaging.stomp.UserPush;
import foc.notification.service.EventProcessor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two replicas on one real broker: this context is replica A (its own
 * {@code notification.push} queue and push listener, started with the
 * context), and the test adds replica B by hand — a second anonymous
 * queue bound to the same exchange, consumed by a container from the
 * same push factory through a second instance of the production adapter.
 * Each replica's {@link LocalUserPushDelivery} is a mock, so what reaches
 * each replica's STOMP broker is observed directly.
 *
 * <p>The push is produced the real way: a processed event commits a row
 * and the after-commit listener publishes. "Exactly once" is proven with
 * a sentinel: queues are FIFO, so once the sentinel's push has reached
 * both replicas, any duplicate of the first would have too.
 */
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=true",
		// Own H2 instance, like the other broker integration tests.
		"spring.datasource.url=jdbc:h2:mem:notification-push-it;DB_CLOSE_DELAY=-1"
})
@Testcontainers(disabledWithoutDocker = true)
class NotificationPushFanoutIntegrationTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-11-05T08:00:00Z");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitMq =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	/** Replica A's delivery: this context's. */
	@MockitoBean
	private LocalUserPushDelivery replicaA;

	@Autowired
	private EventProcessor eventProcessor;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ConnectionFactory connectionFactory;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	@Qualifier(NotificationPushFanoutTopology.PUSH_LISTENER_CONTAINER_FACTORY)
	private SimpleRabbitListenerContainerFactory pushListenerFactory;

	// Courier-arrived tells the requester alone (D34): one row, one push.
	private static CourierArrived courierArrived(String eventId, String requesterId) {
		return new CourierArrived(eventId, OCCURRED_AT, "order-service", "c-fan",
				"ord-" + eventId, requesterId, "usr-fan-courier", "Techno Edge", "COM3-01-19", "fanout");
	}

	@Test
	void onePushReachesEveryReplicaExactlyOnce() {
		LocalUserPushDelivery replicaB = mock(LocalUserPushDelivery.class);
		SimpleMessageListenerContainer replicaBContainer = startReplicaB(replicaB);
		try {
			eventProcessor.process(courierArrived("fan-e1", "usr-fan-1"));
			eventProcessor.process(courierArrived("fan-e2", "usr-fan-sentinel"));

			for (LocalUserPushDelivery replica : List.of(replicaA, replicaB)) {
				ArgumentCaptor<UserPush> pushes = ArgumentCaptor.forClass(UserPush.class);
				verify(replica, timeout(15_000).times(2)).deliver(pushes.capture());
				assertThat(pushes.getAllValues()).extracting(UserPush::userId)
						.containsExactly("usr-fan-1", "usr-fan-sentinel");
				UserPush first = pushes.getAllValues().getFirst();
				assertThat(first.destination()).isEqualTo("/queue/notifications");
				assertThat(first.payload().eventType()).isEqualTo("request.courier-arrived");
				assertThat(first.payload().id()).isNotNull();
				assertThat(first.payload().payload()).containsEntry("requestId", "ord-fan-e1");
			}
		} finally {
			replicaBContainer.stop();
		}
	}

	/** A second replica's queue, binding and push listener, on the same broker. */
	private SimpleMessageListenerContainer startReplicaB(LocalUserPushDelivery delivery) {
		RabbitAdmin admin = new RabbitAdmin(connectionFactory);
		AnonymousQueue queue = new AnonymousQueue();
		admin.declareQueue(queue);
		admin.declareBinding(BindingBuilder.bind(queue)
				.to(new FanoutExchange(NotificationPushFanoutTopology.PUSH_EXCHANGE, true, false)));

		RabbitMqUserPushFanout adapter = new RabbitMqUserPushFanout(rabbitTemplate, jsonMapper, delivery);
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("replica-b-push");
		endpoint.setQueueNames(queue.getName());
		endpoint.setMessageListener(adapter::onPush);
		SimpleMessageListenerContainer container = pushListenerFactory.createListenerContainer(endpoint);
		container.afterPropertiesSet();
		container.start();
		return container;
	}
}
