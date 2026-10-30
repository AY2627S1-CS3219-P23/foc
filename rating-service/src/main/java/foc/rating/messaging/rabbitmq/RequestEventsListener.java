/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: the request.completed listener, modeled on credit-service's
 * RequestEventsListener (its failure path shared via RetryingListener,
 * PR #190 team decisions) without the outcome switch — this service's
 * processor has no invalid-state answer: a duplicate is a deliberate
 * discard, everything else either commits or throws.
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

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

import foc.contracts.events.request.RequestCompleted;
import foc.rating.service.EventProcessor;

/**
 * Consumes {@code request.completed} from this service's work queue
 * and settles each delivery on the processor's answer: a normal return
 * (the eligibility row committed, or a duplicate deliberately
 * discarded) is acknowledged — the processor's transaction has
 * committed by the time it returns, so an ack never precedes the row
 * it confirms; a crash in between redelivers the event, and the
 * event-ID dedupe makes the second delivery change nothing. An
 * exception (the transaction rolled back) takes
 * {@link RetryingListener}'s failure path: republished to the retry
 * queue while attempts remain, then dead-lettered.
 *
 * <p>Attempts are counted in the {@value #RETRY_ATTEMPTS_HEADER}
 * header this listener stamps on each republish — RabbitMQ 4 resets
 * the broker's own {@code x-death} count on every client republish
 * (notification-service found this against a real broker). The
 * original delivery is acknowledged only after the broker confirms the
 * retry copy and does not return it; otherwise it is never left
 * unacknowledged: a returned copy means the retry queue does not
 * exist, which no retry can fix — dead-lettered; a nacked, unconfirmed
 * or unsendable copy is a broker in trouble — requeued after a pause
 * of the confirm timeout, using up none of the event's attempts. A
 * copy that timed out may still have landed, so the event can then run
 * as two copies; the processor is idempotent.
 *
 * <p>A message the converter cannot read (an unknown
 * {@code eventType}, a missing required field, a body that is not
 * JSON) never reaches this method: the container rejects it without
 * requeue on first delivery and it is dead-lettered intact. So does a
 * readable event of any type other than {@link RequestCompleted} —
 * only {@code request.completed} is bound, so another type under this
 * key is a producer error, and the payload mismatch is equally fatal.
 */
@Component
class RequestEventsListener extends RetryingListener {

    private static final Logger log = LoggerFactory.getLogger(RequestEventsListener.class);

    private final EventProcessor processor;

    @Autowired
    RequestEventsListener(EventProcessor processor, RabbitTemplate rabbitTemplate,
            @Value("${rating.rabbitmq.retry.max-attempts}") int maxAttempts,
            @Value("${rating.rabbitmq.retry.confirm-timeout-ms}") long confirmTimeoutMs) {
        this(processor, rabbitTemplate, maxAttempts, confirmTimeoutMs, Thread::sleep);
    }

    RequestEventsListener(EventProcessor processor, RabbitTemplate rabbitTemplate, int maxAttempts,
            long confirmTimeoutMs, Pause pause) {
        super(log, rabbitTemplate, RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE, maxAttempts, confirmTimeoutMs,
                pause);
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitMqTopology.REQUEST_EVENTS_QUEUE)
    void onRequestCompleted(@Payload RequestCompleted event, Message amqpMessage, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            processor.process(event);
        } catch (RuntimeException ex) {
            retryOrDeadLetter(event, "request=" + event.requestId(), ex, amqpMessage, channel, deliveryTag);
            return; // not reached: retryOrDeadLetter always throws
        }
        channel.basicAck(deliveryTag, false);
        log.info("{} eventId={} request={} outcome=HANDLED",
                event.eventType(), event.eventId(), event.requestId());
    }
}
