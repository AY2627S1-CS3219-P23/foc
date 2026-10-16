/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-14, issue #171.
 * Scope: the request event listener, written from docs/credit-service.md
 * (D11, the "Request event listener" component, "Failure and retry
 * behaviour") and following notification-service's DomainEventsListener:
 * manual ack after the handler's commit, a conflicting event
 * dead-lettered, any failure republished to the retry queue with an
 * attempt header until the cap. The retry copy is confirmed through
 * correlated confirms, the confirm type this service already uses.
 * Same day, team decisions on review: a retry copy the broker does not
 * accept requeues the original instead of leaving it unacknowledged,
 * and the two dead-letters are logged at ERROR.
 * 2026-10-16, PR #190 review (Leong Wei Zhi), team decisions
 * transcribed: a returned retry copy dead-letters the original; a
 * nacked or unconfirmed one requeues it after a pause of the confirm
 * timeout, which is now configurable. Also from the review: the outcome
 * switch made exhaustive, any failure to publish the copy settles the
 * delivery, and the event's own failure kept on the requeue path.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import foc.contracts.events.request.RequestEvent;
import foc.credit.service.RequestEventHandler;

/**
 * Consumes the four request events from this service's work queue and
 * settles each delivery on the handler's answer (D11):
 *
 * <ul>
 * <li>{@code HANDLED}: acknowledged. The handler's transaction has
 * committed by the time it returns, so an ack never precedes the
 * balance change it confirms; a crash in between redelivers the event,
 * and its record makes the second delivery change nothing.</li>
 * <li>{@code INVALID_STATE}: nacked without requeue, so the work queue's
 * dead-letter leg files it in the DLQ at once. Retrying a conflict
 * with the request's record could never succeed.</li>
 * <li>An exception (the transaction rolled back): republished to the
 * retry queue while attempts remain, then dead-lettered. This includes a
 * {@code request.completed} that overtook its reservation, which the
 * handler throws for so that it is retried.</li>
 * </ul>
 *
 * <p>Attempts are counted in the {@value #RETRY_ATTEMPTS_HEADER} header
 * this listener stamps on each republish. RabbitMQ 4 resets the
 * broker's own {@code x-death} count on every client republish, so it
 * cannot serve (notification-service found this against a real
 * broker). The original delivery is acknowledged only after the broker
 * confirms the retry copy and does not return it. Otherwise (team
 * decisions, recorded in the design doc's failure table) it is never
 * left unacknowledged:
 *
 * <ul>
 * <li>A returned copy means the retry queue does not exist, which no
 * retry can fix: the original is dead-lettered.</li>
 * <li>A nacked or unconfirmed copy, or one that could not be sent, is
 * a broker in trouble: the original is requeued after a pause of the
 * confirm timeout, so the loop runs once per pause rather than
 * thousands of times a second, and uses up none of the event's
 * attempts. A copy that timed out may still have landed, so the event
 * can then run as two copies; the handler is idempotent.</li>
 * </ul>
 *
 * <p>The three dead-letters this class decides (a conflict, the attempt
 * cap, a returned retry copy) are logged at ERROR: each is a credit operation that will not
 * happen without someone stepping in. Retries and requeues stay at WARN.
 *
 * <p>A message the converter cannot read (a fractional reward, an
 * unknown {@code eventType}, a body that is not a request event) never
 * reaches this method: the container rejects it without requeue on
 * first delivery and it is dead-lettered.
 */
@Component
class RequestEventsListener {

	/** Attempts already made, stamped on each republish (see class doc). */
	static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

	/** The broker's default exchange routes by queue name. */
	private static final String DEFAULT_EXCHANGE = "";

	private static final Logger log = LoggerFactory.getLogger(RequestEventsListener.class);

	/** Waits before a requeue; replaced in the unit tests. */
	@FunctionalInterface
	interface Pause {
		void sleep(long millis) throws InterruptedException;
	}

	private final RequestEventHandler handler;
	private final RabbitTemplate rabbitTemplate;
	private final int maxAttempts;
	private final long confirmTimeoutMs;
	private final Pause pause;

	@Autowired
	RequestEventsListener(RequestEventHandler handler, RabbitTemplate rabbitTemplate,
			@Value("${credit.rabbitmq.retry.max-attempts}") int maxAttempts,
			@Value("${credit.rabbitmq.retry.confirm-timeout-ms}") long confirmTimeoutMs) {
		this(handler, rabbitTemplate, maxAttempts, confirmTimeoutMs, Thread::sleep);
	}

	RequestEventsListener(RequestEventHandler handler, RabbitTemplate rabbitTemplate, int maxAttempts,
			long confirmTimeoutMs, Pause pause) {
		this.handler = handler;
		this.rabbitTemplate = rabbitTemplate;
		this.maxAttempts = maxAttempts;
		this.confirmTimeoutMs = confirmTimeoutMs;
		this.pause = pause;
	}

	@RabbitListener(queues = RabbitMqTopology.REQUEST_EVENTS_QUEUE)
	void onRequestEvent(@Payload RequestEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		RequestEventHandler.Outcome outcome;
		try {
			outcome = handler.handle(event);
		} catch (RuntimeException ex) {
			long attempts = attemptsSoFar(amqpMessage);
			if (attempts >= maxAttempts) {
				channel.basicNack(deliveryTag, false, false);
				log.error("{} eventId={} request={} outcome=DEAD_LETTERED after {} attempts: {}",
						event.eventType(), event.eventId(), event.requestId(), attempts, ex.getMessage());
			} else {
				try {
					scheduleRetry(amqpMessage, attempts);
				} catch (RetryCopyReturnedException returned) {
					// The retry queue does not exist, and no retry can change
					// that: dead-letter the original rather than requeue it
					// forever.
					channel.basicNack(deliveryTag, false, false);
					log.error("{} eventId={} request={} outcome=DEAD_LETTERED: {}; the event failed with: {}",
							event.eventType(), event.eventId(), event.requestId(), returned.getMessage(),
							ex.getMessage());
					returned.addSuppressed(ex);
					throw returned;
				} catch (RuntimeException publishFailure) {
					// The broker is in trouble, which says nothing about the
					// event: wait, then requeue the original rather than leave it
					// unacknowledged, invisible until the channel closes. The
					// requeued delivery has no new attempt stamped, so this uses
					// up none of the event's attempts.
					pauseBeforeRequeue();
					channel.basicNack(deliveryTag, false, true);
					log.warn("{} eventId={} request={} outcome=REQUEUED after {} ms: retry copy not accepted ({});"
							+ " the event failed with: {}",
							event.eventType(), event.eventId(), event.requestId(), confirmTimeoutMs,
							publishFailure, ex.getMessage());
					publishFailure.addSuppressed(ex);
					throw publishFailure;
				}
				channel.basicAck(deliveryTag, false);
				log.warn("{} eventId={} request={} outcome=RETRY after attempt {}/{} failed: {}",
						event.eventType(), event.eventId(), event.requestId(), attempts, maxAttempts,
						ex.getMessage());
			}
			// Rethrown for the container's failure log only. In MANUAL mode
			// the container settles nothing itself unless the exception is a
			// ManualAckListenerExecutionRuntimeException, so this delivery is
			// not settled twice.
			throw ex;
		}
		// A switch expression, so a new Outcome does not compile until it is
		// settled here (a switch statement on an enum need not be exhaustive).
		boolean deadLetter = switch (outcome) {
			case HANDLED -> false;
			case INVALID_STATE -> true;
		};
		if (deadLetter) {
			channel.basicNack(deliveryTag, false, false);
			log.error("{} eventId={} request={} outcome=DEAD_LETTERED: conflicts with the request's record",
					event.eventType(), event.eventId(), event.requestId());
		} else {
			channel.basicAck(deliveryTag, false);
			log.info("{} eventId={} request={} outcome=HANDLED",
					event.eventType(), event.eventId(), event.requestId());
		}
	}

	private void pauseBeforeRequeue() {
		try {
			pause.sleep(confirmTimeoutMs);
		} catch (InterruptedException ex) {
			// requeue anyway: the delivery must be settled
			Thread.currentThread().interrupt();
		}
	}

	/** 1 for the delivery in hand, plus the attempts already stamped. */
	private static long attemptsSoFar(Message message) {
		Object priorAttempts = message.getMessageProperties().getHeader(RETRY_ATTEMPTS_HEADER);
		return priorAttempts instanceof Number prior ? 1 + prior.longValue() : 1;
	}

	private void scheduleRetry(Message message, long attempts) {
		message.getMessageProperties().setHeader(RETRY_ATTEMPTS_HEADER, attempts);
		// a received message carries only its receivedDeliveryMode; without
		// this the copy would be transient and lost on a broker restart
		message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		CorrelationData correlation = new CorrelationData();
		rabbitTemplate.send(DEFAULT_EXCHANGE, RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, message, correlation);

		CorrelationData.Confirm confirm;
		try {
			confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AmqpException("interrupted waiting for the retry copy's confirm", ex);
		} catch (ExecutionException | TimeoutException ex) {
			throw new AmqpException("retry copy not confirmed within " + confirmTimeoutMs + " ms", ex);
		}
		if (!confirm.ack()) {
			throw new AmqpException("retry copy nacked" + (confirm.reason() == null ? "" : ": " + confirm.reason()));
		}
		if (correlation.getReturned() != null) {
			// the retry queue is missing: the copy went nowhere
			throw new RetryCopyReturnedException(correlation.getReturned().getReplyText());
		}
	}

	/** The retry queue does not exist; retrying the publish cannot succeed. */
	static final class RetryCopyReturnedException extends AmqpException {

		RetryCopyReturnedException(String replyText) {
			super("retry copy returned as unroutable: " + replyText);
		}
	}
}
