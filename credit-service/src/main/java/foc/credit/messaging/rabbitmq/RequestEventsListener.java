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
 * 2026-10-25, issue #235: the class comment counts five consumed events.
 * 2026-10-28, issue #267: the failure path moved unchanged to
 * RetryingListener, which the user-events listener shares.
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

import foc.contracts.events.request.RequestEvent;
import foc.credit.service.RequestEventHandler;

/**
 * Consumes the five request events from this service's work queue and
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
 * {@code request.completed} or {@code request.dispute-resolved} that
 * overtook its reservation, which the
 * handler throws for so that it is retried.</li>
 * </ul>
 *
 * <p>The failure path is {@link RetryingListener}'s, shared with
 * {@link UserEventsListener}. Attempts are counted in the
 * {@value #RETRY_ATTEMPTS_HEADER} header
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
class RequestEventsListener extends RetryingListener {

	private static final Logger log = LoggerFactory.getLogger(RequestEventsListener.class);

	private final RequestEventHandler handler;

	@Autowired
	RequestEventsListener(RequestEventHandler handler, RabbitTemplate rabbitTemplate,
			@Value("${credit.rabbitmq.retry.max-attempts}") int maxAttempts,
			@Value("${credit.rabbitmq.retry.confirm-timeout-ms}") long confirmTimeoutMs) {
		this(handler, rabbitTemplate, maxAttempts, confirmTimeoutMs, Thread::sleep);
	}

	RequestEventsListener(RequestEventHandler handler, RabbitTemplate rabbitTemplate, int maxAttempts,
			long confirmTimeoutMs, Pause pause) {
		super(log, rabbitTemplate, RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, maxAttempts, confirmTimeoutMs,
				pause);
		this.handler = handler;
	}

	@RabbitListener(queues = RabbitMqTopology.REQUEST_EVENTS_QUEUE)
	void onRequestEvent(@Payload RequestEvent event, Message amqpMessage, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		RequestEventHandler.Outcome outcome;
		try {
			outcome = handler.handle(event);
		} catch (RuntimeException ex) {
			retryOrDeadLetter(event, "request=" + event.requestId(), ex, amqpMessage, channel, deliveryTag);
			return; // not reached: retryOrDeadLetter always throws
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
}
