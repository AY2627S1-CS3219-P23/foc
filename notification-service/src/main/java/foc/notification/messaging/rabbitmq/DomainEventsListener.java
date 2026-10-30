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
 * 2026-10-30, Claude Code (Fable 5), issue #255: the rating work queue
 * joins the queues list, and scheduleRetry derives each delivery's
 * retry queue from its consuming queue (the <work>.retry naming
 * invariant) instead of hard-coding the request retry queue — a failed
 * rating event would otherwise park in the request trio. Author
 * review: Leong Wei Zhi, pending pull request review.
 * 2026-10-30, PR #276 review (@Sinnez1): the name-derivation replaced
 * by an explicit work→retry queue map over the topology constants — a
 * default-exchange publish to a nonexistent queue is dropped yet still
 * confirmed, so the derived name could silently lose the event; and
 * the missing-consumer-queue guard now nacks with requeue instead of
 * throwing (in MANUAL mode an uncaught exception settles nothing and
 * the delivery would pin a prefetch slot until restart).
 */
package foc.notification.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import foc.contracts.events.core.DomainEvent;
import foc.notification.service.EventProcessor;
import java.io.IOException;
import java.util.Map;
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
 * everywhere else. A confirm does <em>not</em> vouch for routing,
 * though: the broker confirms a default-exchange publish to a
 * nonexistent queue while dropping the message, which is why the retry
 * target comes from {@link #RETRY_QUEUE_BY_WORK_QUEUE} — a closed map
 * over the queues the topology declares at startup — rather than a
 * name built at runtime (PR #276 review).
 *
 * <p>Messages the converter rejects (unknown eventType, malformed
 * body) never reach this method: the container rejects them without
 * requeue on first delivery and they dead-letter straight to the DLQ —
 * retrying them could never succeed (author decision, 2026-09-20).
 *
 * <p>Event-type-agnostic by construction: any event the registry
 * catalogs flows through unchanged, so a new domain queue is one more
 * entry in the {@code queues} list below plus its row in
 * {@link #RETRY_QUEUE_BY_WORK_QUEUE} (the rating queue arrived with
 * issue #255). Nothing here is domain-specific: the retry target is
 * looked up per delivery from its consuming queue; a delivery the map
 * cannot place is nacked with requeue — settled, so it never pins a
 * prefetch slot, and redelivered rather than guessed at.
 */
@Component
class DomainEventsListener {

	/** Attempts already made, stamped on each republish (see class doc). */
	static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

	/**
	 * Retry queue per work queue, closed over the topology's declared
	 * constants: the publish target provably exists, where a runtime-built
	 * {@code <queue>.retry} name could point at an undeclared queue — and
	 * an unroutable default-exchange publish is dropped but still
	 * confirmed, slipping past {@code waitForConfirmsOrDie} (PR #276
	 * review). A queue added to the listener without a row here fails
	 * loudly via the requeue path in {@code onDomainEvent}.
	 */
	static final Map<String, String> RETRY_QUEUE_BY_WORK_QUEUE = Map.of(
			RabbitMqTopology.REQUEST_EVENTS_QUEUE, RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE,
			RabbitMqTopology.RATING_EVENTS_QUEUE, RabbitMqTopology.RATING_EVENTS_RETRY_QUEUE);

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

	@RabbitListener(queues = {
			RabbitMqTopology.REQUEST_EVENTS_QUEUE,
			RabbitMqTopology.RATING_EVENTS_QUEUE })
	void onDomainEvent(@Payload DomainEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		try {
			eventProcessor.process(event);
		} catch (RuntimeException ex) {
			long attempts = attemptsSoFar(amqpMessage);
			log.warn("event {} failed on attempt {}/{}", event.eventId(), attempts, maxAttempts);
			// Map.of rejects null keys, so the absent-header case is guarded
			// explicitly and lands in the same requeue branch as an unmapped
			// queue.
			String consumerQueue = amqpMessage.getMessageProperties().getConsumerQueue();
			String retryQueue =
					consumerQueue == null ? null : RETRY_QUEUE_BY_WORK_QUEUE.get(consumerQueue);
			if (attempts >= maxAttempts) {
				// Exhausted: the work queue's dead-letter leg files it
				// in the DLQ (F2.3).
				channel.basicNack(deliveryTag, false, false);
			} else if (retryQueue == null) {
				// No consumer queue on the delivery, or a queue missing its
				// RETRY_QUEUE_BY_WORK_QUEUE row: requeue instead of guessing
				// a target — settled here because in MANUAL mode nothing
				// else would, and the delivery would pin a prefetch slot
				// until restart (PR #276 review).
				log.error("event {} has no mapped retry queue (consumer queue {}), requeueing",
						event.eventId(), consumerQueue);
				channel.basicNack(deliveryTag, false, true);
			} else {
				scheduleRetry(amqpMessage, attempts, retryQueue);
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

	/** Parks the failed delivery in its own trio's retry queue. */
	private void scheduleRetry(Message message, long attempts, String retryQueue) {
		message.getMessageProperties().setHeader(RETRY_ATTEMPTS_HEADER, attempts);
		message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		// Confirmed publish (needs spring.rabbitmq.publisher-confirm-type:
		// simple): the caller acks the original only after this returns,
		// so a publish the broker never accepted throws instead of
		// silently losing the event.
		rabbitTemplate.invoke(operations -> {
			operations.send("", retryQueue, message);
			operations.waitForConfirmsOrDie(RETRY_CONFIRM_TIMEOUT_MS);
			return null;
		});
	}
}
