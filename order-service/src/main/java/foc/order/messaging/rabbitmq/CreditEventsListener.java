/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: AMQP listener for the Credit Service's replies, a copy of
 * notification-service's DomainEventsListener (Claude Code (Fable 5),
 * reviewed by Leong Wei Zhi): manual ack after the handler returns,
 * retry by republishing to the retry queue with an attempts header,
 * dead-letter once the attempts run out, with credit-service's D11
 * values (2 s, 5 attempts) by the author's decision. Two changes: a
 * reply naming no known request is dead-lettered at once (the copied
 * D11 rule for conflicting events), and the retry republish waits on a
 * correlated confirm, since the outbox publisher needs the connection's
 * correlated confirm mode.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import foc.contracts.events.core.DomainEvent;
import foc.contracts.events.credit.CreditEvent;
import foc.order.CreditReplyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thin inbound adapter: the message converter has already turned the
 * body into its event record (dispatch on the body's eventType); this
 * class hands it to the {@link CreditReplyHandler} and settles the
 * delivery on the result. Acked only after the handler returns, so the
 * state change commits before the ack: a crash in between redelivers
 * the reply, and the handler ignores a reply for a request that is no
 * longer PENDING.
 *
 * <p>Failure handling: while attempts remain, a failed reply is
 * republished to the retry queue (forced PERSISTENT, with the attempts
 * so far in {@value #RETRY_ATTEMPTS_HEADER}) and the original is acked;
 * the broker returns it to the work queue when the retry TTL expires.
 * Once {@code order.rabbitmq.retry.max-attempts} is reached it is
 * nacked without requeue, and the work queue's dead-letter leg files it
 * in the DLQ. The republish is confirmed before the original is acked,
 * so a lost republish throws and leaves the original unacked for
 * redelivery instead of losing the reply.
 *
 * <p>Messages the converter rejects never reach this method: the
 * container rejects them without requeue and they go straight to the
 * DLQ.
 */
@Component
class CreditEventsListener {

	/** Attempts already made, stamped on each republish. */
	static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

	/** Ceiling on waiting for the broker to confirm a retry republish. */
	private static final long RETRY_CONFIRM_TIMEOUT_MS = 5_000;

	private static final Logger log = LoggerFactory.getLogger(CreditEventsListener.class);

	private final CreditReplyHandler handler;
	private final RabbitTemplate rabbitTemplate;
	private final int maxAttempts;

	CreditEventsListener(CreditReplyHandler handler, RabbitTemplate rabbitTemplate,
			@Value("${order.rabbitmq.retry.max-attempts}") int maxAttempts) {
		this.handler = handler;
		this.rabbitTemplate = rabbitTemplate;
		this.maxAttempts = maxAttempts;
	}

	@RabbitListener(queues = RabbitMqTopology.CREDIT_EVENTS_QUEUE)
	void onCreditEvent(@Payload DomainEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		if (!(event instanceof CreditEvent reply)) {
			// only credit replies are bound to this queue; one published
			// here by hand is acknowledged, but logged so it can be traced
			log.warn("Non-credit event {} (eventId {}) on {}; acknowledged without action",
					event.eventType(), event.eventId(), RabbitMqTopology.CREDIT_EVENTS_QUEUE);
			channel.basicAck(deliveryTag, false);
			return;
		}
		CreditReplyHandler.Outcome outcome;
		try {
			outcome = handler.handle(reply);
		} catch (RuntimeException ex) {
			long attempts = attemptsSoFar(amqpMessage);
			log.warn("reply {} failed on attempt {}/{}", event.eventId(), attempts, maxAttempts);
			if (attempts >= maxAttempts) {
				// exhausted: the work queue's dead-letter leg files it in the DLQ
				channel.basicNack(deliveryTag, false, false);
			} else {
				scheduleRetry(amqpMessage, attempts);
				channel.basicAck(deliveryTag, false);
			}
			// rethrown for the container's failure logging only; in MANUAL
			// mode it does not settle the delivery a second time
			throw ex;
		}
		switch (outcome) {
			case HANDLED -> channel.basicAck(deliveryTag, false);
			// no retry could find the request: straight to the DLQ
			case UNKNOWN_REQUEST -> channel.basicNack(deliveryTag, false, false);
		}
	}

	/** 1 for the delivery in hand, plus the attempts already stamped. */
	private long attemptsSoFar(Message message) {
		Object priorAttempts = message.getMessageProperties().getHeader(RETRY_ATTEMPTS_HEADER);
		return priorAttempts instanceof Number prior ? 1 + prior.longValue() : 1;
	}

	// Republishes to the retry queue and waits for the broker to confirm
	// it; anything short of a clean confirm throws, so the caller never
	// acks the original.
	private void scheduleRetry(Message message, long attempts) {
		message.getMessageProperties().setHeader(RETRY_ATTEMPTS_HEADER, attempts);
		message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
		rabbitTemplate.send("", RabbitMqTopology.CREDIT_EVENTS_RETRY_QUEUE, message, correlation);
		CorrelationData.Confirm confirm;
		try {
			confirm = correlation.getFuture().get(RETRY_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AmqpException("interrupted waiting for the retry confirm", ex);
		} catch (ExecutionException | TimeoutException ex) {
			throw new AmqpException("retry republish not confirmed", ex);
		}
		if (!confirm.ack() || correlation.getReturned() != null) {
			throw new AmqpException("retry republish refused by the broker");
		}
	}
}
