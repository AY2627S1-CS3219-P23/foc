/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — publishes each push to the
 * fanout exchange and delivers every push it receives to this replica's
 * STOMP broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import foc.notification.messaging.stomp.LocalUserPushDelivery;
import foc.notification.messaging.stomp.UserPush;
import foc.notification.messaging.stomp.UserPushFanout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AMQP adapter of {@link UserPushFanout} (DP10). A push goes out as
 * the {@link UserPush} JSON, written by Boot's mapper (the one the STOMP
 * converter uses), to {@value NotificationPushFanoutTopology#PUSH_EXCHANGE};
 * every replica's queue gets a copy, and {@link #onPush} hands it to
 * {@link LocalUserPushDelivery}. The publisher never delivers locally:
 * its copy arrives through its own queue like everyone else's.
 *
 * <p>Pushes are fire-and-forget: sent non-persistent (the queues are not
 * durable anyway), acknowledged automatically, and a push that cannot be
 * read or delivered is logged and dropped — the REST list is the
 * catch-up path (issue #66). The message is built here and sent with
 * {@code send}, so the domain-event converter is never involved.
 */
@Component
class RabbitMqUserPushFanout implements UserPushFanout {

	/** Fanout exchanges ignore the routing key. */
	private static final String NO_ROUTING_KEY = "";

	private static final Logger log = LoggerFactory.getLogger(RabbitMqUserPushFanout.class);

	private final RabbitTemplate rabbitTemplate;
	private final JsonMapper jsonMapper;
	private final LocalUserPushDelivery localDelivery;

	RabbitMqUserPushFanout(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper,
			LocalUserPushDelivery localDelivery) {
		this.rabbitTemplate = rabbitTemplate;
		this.jsonMapper = jsonMapper;
		this.localDelivery = localDelivery;
	}

	@Override
	public void publish(UserPush push) {
		Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(push))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
				.build();
		rabbitTemplate.send(NotificationPushFanoutTopology.PUSH_EXCHANGE, NO_ROUTING_KEY, message);
	}

	@RabbitListener(queues = "#{notificationPushQueue.name}",
			containerFactory = NotificationPushFanoutTopology.PUSH_LISTENER_CONTAINER_FACTORY)
	void onPush(Message message) {
		try {
			localDelivery.deliver(jsonMapper.readValue(message.getBody(), UserPush.class));
		} catch (RuntimeException ex) {
			log.warn("dropped a fanned-out push that could not be delivered", ex);
		}
	}
}
