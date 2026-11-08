/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: the fanout adapter's two halves — the publish targets the fanout
 * exchange with a transient JSON envelope that decodes back to the same
 * push, and a received message is handed to the local delivery; an
 * undecodable one is dropped without throwing (nothing to requeue).
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RabbitMqUserPushFanoutTest {

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final LocalUserPushDelivery localDelivery = mock(LocalUserPushDelivery.class);
	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private final RabbitMqUserPushFanout fanout =
			new RabbitMqUserPushFanout(rabbitTemplate, objectMapper, localDelivery);

	private static final UserPush PUSH = new UserPush("usr-req-1001", new NotificationDto(42L,
			"request.accepted", Map.of("requestId", "req-1"),
			Instant.parse("2026-09-19T08:30:00Z"), false, Instant.parse("2026-09-19T08:30:01Z")));

	@Test
	void publishesATransientJsonEnvelopeToTheFanoutExchange() {
		fanout.publish(PUSH);

		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq(RabbitMqUserPushFanout.PUSH_EXCHANGE), eq(""), sent.capture());
		MessageProperties properties = sent.getValue().getMessageProperties();
		assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
		assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
		assertThat(objectMapper.readValue(sent.getValue().getBody(), UserPush.class)).isEqualTo(PUSH);
		verifyNoInteractions(localDelivery);
	}

	@Test
	void aReceivedPushIsDeliveredLocally() {
		fanout.onPush(new Message(objectMapper.writeValueAsBytes(PUSH), new MessageProperties()));

		verify(localDelivery).deliver(PUSH);
	}

	@Test
	void anUndecodablePushIsDroppedNotRethrown() {
		fanout.onPush(new Message("not json".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

		verifyNoInteractions(localDelivery);
	}
}
