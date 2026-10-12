/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: publishes one outbox reply to the credit-events exchange with
 * the mandatory flag and a correlated publisher confirm
 * (docs/credit-service.md D10). The 5 s confirm wait and which failures
 * stop a poll are the team's decisions: only a returned reply is about
 * that one reply; a nack or a missing confirm means the broker is in
 * trouble (revised the same day, replacing a first version that
 * skipped them).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import foc.contracts.events.core.EventContracts;
import foc.credit.entity.OutboxEvent;
import foc.credit.service.ReplyPublisher;

/**
 * Sends a stored reply to the {@code credit-events} exchange, routed by
 * its event type, and waits for the broker's answer. The stored payload
 * is the message body as it is; nothing is converted here.
 *
 * <p>Needs {@code spring.rabbitmq.publisher-confirm-type: correlated},
 * {@code publisher-returns} and {@code template.mandatory} (set in
 * application.yaml): the broker returns a reply no queue took before it
 * confirms it, so by the time the confirm arrives the return, if any,
 * is already on the {@link CorrelationData}.
 */
@Component
class RabbitReplyPublisher implements ReplyPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitReplyPublisher.class);

	private final RabbitTemplate rabbitTemplate;
	private final long confirmTimeoutMs;

	RabbitReplyPublisher(RabbitTemplate rabbitTemplate,
			@Value("${credit.outbox.publisher.confirm-timeout-ms}") long confirmTimeoutMs) {
		this.rabbitTemplate = rabbitTemplate;
		this.confirmTimeoutMs = confirmTimeoutMs;
		// A return is read from the CorrelationData in publish(). Without
		// a callback the template also logs "Returned message but no
		// callback available" for every one, so it is given one that has
		// nothing left to do.
		rabbitTemplate.setReturnsCallback(returned -> { });
	}

	@Override
	public Result publish(OutboxEvent reply) {
		String eventId = reply.getEventId().toString();
		CorrelationData correlation = new CorrelationData(eventId);
		Message message = MessageBuilder.withBody(reply.getPayload().getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setMessageId(eventId)
				// survives a broker restart once a durable queue holds it
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.build();
		try {
			rabbitTemplate.send(EventContracts.CREDIT_EVENTS_EXCHANGE, reply.getEventType(), message, correlation);
		} catch (AmqpException ex) {
			// the reply never left: no connection or no channel
			log.warn("Reply {} ({}) not published: broker unreachable: {}",
					eventId, reply.getEventType(), ex.getMessage());
			return Result.BROKER_UNAVAILABLE;
		}

		CorrelationData.Confirm confirm;
		try {
			confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
		} catch (TimeoutException ex) {
			// a broker that does not answer would cost every other reply
			// the same wait
			log.warn("Reply {} ({}) not confirmed: no confirm from the broker within {} ms",
					eventId, reply.getEventType(), confirmTimeoutMs);
			return Result.BROKER_UNAVAILABLE;
		} catch (ExecutionException ex) {
			log.warn("Reply {} ({}) not confirmed: waiting for the confirm failed: {}",
					eventId, reply.getEventType(), ex.getMessage());
			return Result.BROKER_UNAVAILABLE;
		} catch (InterruptedException ex) {
			// the service is shutting down: stop the poll, the reply
			// stays unsent for the next start
			Thread.currentThread().interrupt();
			return Result.BROKER_UNAVAILABLE;
		}

		if (!confirm.ack()) {
			// The broker nacks only on an error of its own, and the client
			// nacks a confirm that will never come because the channel
			// closed. Neither is about this reply, so they are not told apart.
			log.warn("Reply {} ({}) not confirmed: nacked{}", eventId, reply.getEventType(),
					confirm.reason() == null ? "" : ": " + confirm.reason());
			return Result.BROKER_UNAVAILABLE;
		}
		ReturnedMessage returned = correlation.getReturned();
		if (returned != null) {
			log.warn("Reply {} ({}) stays unsent: returned as unroutable ({} {}); is a queue bound to {}?",
					eventId, reply.getEventType(), returned.getReplyCode(), returned.getReplyText(),
					EventContracts.CREDIT_EVENTS_EXCHANGE);
			return Result.NOT_SENT;
		}
		return Result.SENT;
	}
}
