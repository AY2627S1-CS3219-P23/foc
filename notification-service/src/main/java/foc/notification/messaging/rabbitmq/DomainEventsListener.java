/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: AMQP listener adapter for issue #64 per team decisions D11
 * (thin adapter, business logic behind the EventProcessor port) and
 * the author's ack/nack decisions: literal manual ack after the
 * processor's transaction commits (NFR1.1); nack with requeue on
 * failure, with backoff/attempt limits deferred to issue #65.
 * 2026-09-19, Method-B refactor (D16-D19): parameter type moves from
 * the deleted EventEnvelope to the typed DomainEvent contract; same
 * day, renamed OrderEventsListener -> DomainEventsListener (author's
 * naming decision — the class was already event-type-agnostic).
 * 2026-09-20, issue #65: failure handling per team decisions D6/D7
 * (10 s TTL, 3 attempts, decided 2026-09-19) — republish to the retry
 * queue while attempts remain (counted via a service-set header;
 * RabbitMQ 4 resets x-death counts on client republish, verified
 * against a real broker), nack without requeue to the DLQ once
 * exhausted. Same day, PR #79 Copilot review: the republish is now a
 * confirmed publish, so the original is acked only after the broker
 * accepts the retry copy.
 * 2026-09-20: order→request event vocabulary rename applied (author
 * decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import foc.contracts.events.core.DomainEvent;
import foc.notification.service.EventProcessor;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D11): deserialization to the concrete event
 * record happens in the message converter before this method runs
 * (dispatch on the body's eventType, D18); this class only hands the
 * event to the {@link EventProcessor} port and settles the delivery on
 * the result. Manual ack after the processor returns means the DB
 * commit strictly precedes the ack — a crash in between causes
 * redelivery, never loss, and the processor's dedupe absorbs the
 * redelivered copy (NFR1.1).
 *
 * <p>Failure handling (D6/D7, F2.2/F2.3): while attempts remain, a
 * failed event is republished to the retry queue — forced PERSISTENT,
 * since a received message carries only {@code receivedDeliveryMode}
 * and a naive republish would be transient, violating NFR1.2 — and the
 * original delivery is acked; the broker returns it to the work queue
 * when the retry TTL expires. Attempts are counted via the
 * {@value #RETRY_ATTEMPTS_HEADER} header this listener stamps on each
 * republish, so the count rides with the message across restarts; the
 * broker's own {@code x-death} count cannot serve here — RabbitMQ 4
 * resets it to 1 on every client republish (observed against a real
 * broker in the integration test). Once
 * {@code notification.rabbitmq.retry.max-attempts} is reached, the
 * event is nacked without requeue and the work queue's dead-letter
 * exchange files it in the DLQ.
 *
 * <p>The republish is a <em>confirmed</em> publish (publisher
 * confirms, {@code waitForConfirmsOrDie}): the original delivery is
 * acked only after the broker accepts the retry copy, so a lost or
 * refused publish surfaces as an exception and leaves the original
 * unacked for redelivery — the failed event can never vanish. A crash
 * between confirm and ack duplicates at most one retry — absorbed by
 * the processor's event-ID dedupe, the same at-least-once posture as
 * everywhere else.
 *
 * <p>Messages the converter rejects (unknown eventType, malformed
 * body) never reach this method: the container rejects them without
 * requeue on first delivery and they dead-letter straight to the DLQ —
 * retrying them could never succeed (author decision, 2026-09-20).
 *
 * <p>Event-type-agnostic by construction: any event the registry
 * catalogs flows through unchanged, so a future second domain queue
 * is one more entry in the {@code queues} list below — the class
 * never changes. Only the queue references are domain-specific.
 */
@Component
class DomainEventsListener {

	/** Attempts already made, stamped on each republish (see class doc). */
	static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

	/**
	 * Ceiling on waiting for the broker to confirm a retry republish;
	 * on timeout the original stays unacked and is redelivered.
	 */
	private static final long RETRY_CONFIRM_TIMEOUT_MS = 5_000;

	private static final Logger log = LoggerFactory.getLogger(DomainEventsListener.class);

	private final EventProcessor eventProcessor;
	private final RabbitTemplate rabbitTemplate;
	private final int maxAttempts;

	DomainEventsListener(EventProcessor eventProcessor, RabbitTemplate rabbitTemplate,
			@Value("${notification.rabbitmq.retry.max-attempts}") int maxAttempts) {
		this.eventProcessor = eventProcessor;
		this.rabbitTemplate = rabbitTemplate;
		this.maxAttempts = maxAttempts;
	}

	@RabbitListener(queues = RabbitMqTopology.REQUEST_EVENTS_QUEUE)
	void onDomainEvent(@Payload DomainEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		try {
			eventProcessor.process(event);
		} catch (RuntimeException ex) {
			long attempts = attemptsSoFar(amqpMessage);
			log.warn("event {} failed on attempt {}/{}", event.eventId(), attempts, maxAttempts);
			if (attempts >= maxAttempts) {
				// Exhausted: the work queue's dead-letter leg files it
				// in the DLQ (F2.3).
				channel.basicNack(deliveryTag, false, false);
			} else {
				scheduleRetry(amqpMessage, attempts);
				channel.basicAck(deliveryTag, false);
			}
			// Rethrow feeds the container's failure logging only — it cannot
			// double-settle this delivery: in MANUAL mode the container nacks
			// solely for ManualAckListenerExecutionRuntimeException (spring-rabbit
			// 4.1.1, BlockingQueueConsumer#rollbackOnExceptionIfNecessary).
			throw ex;
		}
		channel.basicAck(deliveryTag, false);
	}

	/** 1 for the delivery in hand, plus the attempts already stamped. */
	private long attemptsSoFar(Message message) {
		Object priorAttempts = message.getMessageProperties().getHeader(RETRY_ATTEMPTS_HEADER);
		return priorAttempts instanceof Number prior ? 1 + prior.longValue() : 1;
	}

	private void scheduleRetry(Message message, long attempts) {
		message.getMessageProperties().setHeader(RETRY_ATTEMPTS_HEADER, attempts);
		message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		// Confirmed publish (needs spring.rabbitmq.publisher-confirm-type:
		// simple): the caller acks the original only after this returns,
		// so a publish the broker never accepted throws instead of
		// silently losing the event.
		rabbitTemplate.invoke(operations -> {
			operations.send("", RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, message);
			operations.waitForConfirmsOrDie(RETRY_CONFIRM_TIMEOUT_MS);
			return null;
		});
	}
}
