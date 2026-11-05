/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — unit tests for the AMQP
 * adapter (envelope, exchange, no local delivery on publish, received
 * pushes delivered, unreadable ones dropped) and the fanout topology
 * (exchange/queue/binding shape, the listener factory's AUTO ack).
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import foc.notification.messaging.stomp.LocalUserPushDelivery;
import foc.notification.messaging.stomp.UserPush;
import foc.notification.service.NotificationDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import tools.jackson.databind.json.JsonMapper;

class RabbitMqUserPushFanoutTest {

	private static final UserPush PUSH = new UserPush("usr-fan-1", "/queue/notifications",
			new NotificationDto(42L, "request.accepted", Map.of("requestId", "ord-fan-1"),
					Instant.parse("2026-11-05T08:00:00Z"), false, Instant.parse("2026-11-05T08:00:01Z")));

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final LocalUserPushDelivery localDelivery = mock(LocalUserPushDelivery.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final RabbitMqUserPushFanout fanout =
			new RabbitMqUserPushFanout(rabbitTemplate, jsonMapper, localDelivery);

	@Test
	void publishesThePushToTheFanoutExchangeOnly() {
		fanout.publish(PUSH);

		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq("notification.push"), eq(""), sent.capture());
		MessageProperties properties = sent.getValue().getMessageProperties();
		assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
		assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
		assertThat(jsonMapper.readValue(sent.getValue().getBody(), UserPush.class)).isEqualTo(PUSH);
		// the publisher's copy comes back through its own queue
		verifyNoInteractions(localDelivery);
	}

	@Test
	void aReceivedPushIsDeliveredLocally() {
		fanout.onPush(MessageBuilder.withBody(jsonMapper.writeValueAsBytes(PUSH)).build());

		verify(localDelivery).deliver(PUSH);
	}

	@Test
	void anUnreadablePushIsDroppedNotThrown() {
		Message garbage = MessageBuilder.withBody("not json".getBytes(StandardCharsets.UTF_8)).build();

		assertThatCode(() -> fanout.onPush(garbage)).doesNotThrowAnyException();
		verifyNoInteractions(localDelivery);
	}

	@Test
	void eachReplicaBindsItsOwnTransientQueueToTheFanoutExchange() {
		NotificationPushFanoutTopology topology = new NotificationPushFanoutTopology();
		AnonymousQueue queue = topology.notificationPushQueue();

		Declarables declarables = topology.notificationPushTopology(queue);

		Exchange exchange = declarables.getDeclarablesByType(Exchange.class).getFirst();
		assertThat(exchange.getName()).isEqualTo("notification.push");
		assertThat(exchange.getType()).isEqualTo(ExchangeTypes.FANOUT);
		Queue declaredQueue = declarables.getDeclarablesByType(Queue.class).getFirst();
		assertThat(declaredQueue).isSameAs(queue);
		assertThat(declaredQueue.isDurable()).isFalse();
		assertThat(declaredQueue.isExclusive()).isTrue();
		assertThat(declaredQueue.isAutoDelete()).isTrue();
		// a fresh name per replica
		assertThat(topology.notificationPushQueue().getName()).isNotEqualTo(queue.getName());
		Binding binding = declarables.getDeclarablesByType(Binding.class).getFirst();
		assertThat(binding.getExchange()).isEqualTo("notification.push");
		assertThat(binding.getDestination()).isEqualTo(queue.getName());
	}

	@Test
	void thePushListenerAcksAutomaticallyDespiteTheServiceWideManualMode() {
		RabbitProperties properties = new RabbitProperties();
		properties.getListener().getSimple().setAcknowledgeMode(AcknowledgeMode.MANUAL);

		SimpleMessageListenerContainer container = new NotificationPushFanoutTopology()
				.notificationPushListenerContainerFactory(
						new SimpleRabbitListenerContainerFactoryConfigurer(properties),
						mock(ConnectionFactory.class))
				.createListenerContainer();

		assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
	}
}
