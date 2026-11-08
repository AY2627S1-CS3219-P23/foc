/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35, team
 * decision: AMQP fanout for the cross-replica STOMP push, Leong Wei Zhi
 * via options Q&A; the lean shape of closed PR #291).
 * Scope: the AMQP adapter of the UserPushFanout port — publish to a
 * fanout exchange, receive on a per-replica anonymous queue, hand to the
 * local STOMP delivery. Author review: Leong Wei Zhi, pending pull
 * request review.
 */
package foc.notification.messaging.rabbitmq;

import foc.notification.messaging.stomp.LocalUserPushDelivery;
import foc.notification.messaging.stomp.UserPush;
import foc.notification.messaging.stomp.UserPushFanout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Cross-replica push (D35). A replica that commits a notification
 * publishes the {@link UserPush} to the service-local fanout exchange
 * {@value #PUSH_EXCHANGE}; every replica — the publisher included —
 * consumes it on its own anonymous queue (exclusive, auto-delete,
 * declared by the listener and redeclared on reconnect) and offers it
 * to its own simple broker. A browser's socket lives on exactly one
 * replica, so the frame reaches it once.
 *
 * <p>Wire format is JSON written and read by Boot's mapper directly:
 * the service's AMQP {@code MessageConverter} is the registry-driven
 * {@link DomainEventMessageConverter} (D18), which only knows domain
 * events, so this adapter sends raw bytes and its listener runs on the
 * plain-converter, auto-ack container factory
 * ({@link RabbitMqMessageConverterConfig#PUSH_FANOUT_CONTAINER_FACTORY})
 * and reads the raw {@link Message}. Pushes are transient
 * (non-persistent, no durable queue): a replica that is down has no
 * sockets to miss, and the REST list is the catch-up path (issue #66).
 */
@Component
class RabbitMqUserPushFanout implements UserPushFanout {

	static final String PUSH_EXCHANGE = "notification.push";

	private static final Logger log = LoggerFactory.getLogger(RabbitMqUserPushFanout.class);

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final LocalUserPushDelivery localDelivery;

	RabbitMqUserPushFanout(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
			LocalUserPushDelivery localDelivery) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.localDelivery = localDelivery;
	}

	@Override
	public void publish(UserPush push) {
		Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(push))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
				.build();
		rabbitTemplate.send(PUSH_EXCHANGE, "", message);
	}

	@RabbitListener(containerFactory = RabbitMqMessageConverterConfig.PUSH_FANOUT_CONTAINER_FACTORY,
			bindings = @QueueBinding(
			value = @Queue,
			exchange = @Exchange(value = PUSH_EXCHANGE, type = ExchangeTypes.FANOUT)))
	void onPush(Message message) {
		UserPush push;
		try {
			push = objectMapper.readValue(message.getBody(), UserPush.class);
		} catch (RuntimeException ex) {
			// Not ours to retry: another replica's bad frame would loop forever.
			log.warn("undecodable push dropped", ex);
			return;
		}
		localDelivery.deliver(push);
	}
}
