/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: the user event listener (team decisions USR-1, USR-6), on
 * RequestEventsListener's pattern: manual ack after the handler's
 * commit, any failure republished to the retry queue until the cap, the
 * same failure path (RetryingListener).
 * Same day, PR review (team decision): NO_USER is dead-lettered at once,
 * by an exhaustive switch like RequestEventsListener's.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import foc.contracts.events.user.UserEvent;
import foc.credit.service.AccountResult;
import foc.credit.service.UserEventHandler;

/**
 * Consumes {@code user.created} and {@code user.purged} from this
 * service's user-events work queue. Every outcome the handler returns
 * is acknowledged once its transaction has committed (USR-6: a
 * duplicate or a reordered event is decided by the account's state, and
 * none is a conflict), except {@code NO_USER}, an event naming no user,
 * which is nacked without requeue and so dead-lettered at once. An
 * exception is retried and then dead-lettered, exactly as for a request
 * event ({@link RetryingListener}).
 *
 * <p>A message the converter cannot read (an unknown {@code eventType},
 * a body missing {@code userId}, a request event published here) never
 * reaches this method: the container rejects it without requeue and it
 * is dead-lettered.
 */
@Component
class UserEventsListener extends RetryingListener {

	private static final Logger log = LoggerFactory.getLogger(UserEventsListener.class);

	private final UserEventHandler handler;

	@Autowired
	UserEventsListener(UserEventHandler handler, RabbitTemplate rabbitTemplate,
			@Value("${credit.rabbitmq.retry.max-attempts}") int maxAttempts,
			@Value("${credit.rabbitmq.retry.confirm-timeout-ms}") long confirmTimeoutMs) {
		this(handler, rabbitTemplate, maxAttempts, confirmTimeoutMs, Thread::sleep);
	}

	UserEventsListener(UserEventHandler handler, RabbitTemplate rabbitTemplate, int maxAttempts,
			long confirmTimeoutMs, Pause pause) {
		super(log, rabbitTemplate, RabbitMqTopology.USER_EVENTS_RETRY_QUEUE, maxAttempts, confirmTimeoutMs, pause);
		this.handler = handler;
	}

	@RabbitListener(queues = RabbitMqTopology.USER_EVENTS_QUEUE)
	void onUserEvent(@Payload UserEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		AccountResult result;
		try {
			result = handler.handle(event);
		} catch (RuntimeException ex) {
			retryOrDeadLetter(event, "user=" + event.userId(), ex, amqpMessage, channel, deliveryTag);
			return; // not reached: retryOrDeadLetter always throws
		}
		// A switch expression, so a new AccountResult does not compile until
		// it is settled here.
		boolean deadLetter = switch (result) {
			case CREATED, CLOSED, CLOSED_PLACEHOLDER, DUPLICATE, IGNORED_CLOSED -> false;
			case NO_USER -> true;
		};
		if (deadLetter) {
			channel.basicNack(deliveryTag, false, false);
			log.error("{} eventId={} outcome=DEAD_LETTERED: names no user", event.eventType(), event.eventId());
		} else {
			channel.basicAck(deliveryTag, false);
			log.info("{} eventId={} user={} outcome={}", event.eventType(), event.eventId(), event.userId(),
					result);
		}
	}
}
