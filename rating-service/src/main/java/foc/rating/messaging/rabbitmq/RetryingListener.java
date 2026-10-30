/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: a copy of credit-service's RetryingListener (Claude Code
 * (Opus 5.5), 2026-10-28, issue #267 — itself the failure path of its
 * request listener, PR #190 team decisions: the attempt header, the
 * confirmed retry copy, the dead-letter on a returned copy, the paused
 * requeue otherwise), with only the package changed. Copied, not
 * shared, as the team does for everything but foc-contracts.
 * Author review: [pending]
 * 2026-10-30, Claude Code (Fable 5), PR #271 review (Sinnez1): the
 * final rethrow is wrapped in EventFailedException so the container's
 * error handler can never classify it fatal — a fatal classification
 * in MANUAL mode rejects the already-settled delivery (rejectManual)
 * and closes the channel with PRECONDITION_FAILED. A divergence from
 * credit-service's copy, where the same edge exists.
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.rabbitmq.client.Channel;

import foc.contracts.events.core.DomainEvent;

/**
 * What a listener does with a delivery whose handler threw (the
 * transaction rolled back): republish it to the retry queue while
 * attempts remain, then dead-letter it. See {@link RequestEventsListener}
 * for the rules.
 */
abstract class RetryingListener {

    /** Attempts already made, stamped on each republish (see RequestEventsListener). */
    static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

    /** The broker's default exchange routes by queue name. */
    private static final String DEFAULT_EXCHANGE = "";

    /** Waits before a requeue; replaced in the unit tests. */
    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    private final Logger log;
    private final RabbitTemplate rabbitTemplate;
    private final String retryQueue;
    private final int maxAttempts;
    private final long confirmTimeoutMs;
    private final Pause pause;

    RetryingListener(Logger log, RabbitTemplate rabbitTemplate, String retryQueue, int maxAttempts,
            long confirmTimeoutMs, Pause pause) {
        this.log = log;
        this.rabbitTemplate = rabbitTemplate;
        this.retryQueue = retryQueue;
        this.maxAttempts = maxAttempts;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.pause = pause;
    }

    /**
     * Settles the delivery of an event whose handler threw {@code ex},
     * then throws it wrapped in {@link EventFailedException} for the
     * container's failure log. {@code subject} names what the event is
     * about in the log lines, e.g. {@code request=42}.
     */
    final void retryOrDeadLetter(DomainEvent event, String subject, RuntimeException ex, Message amqpMessage,
            Channel channel, long deliveryTag) throws IOException {
        long attempts = attemptsSoFar(amqpMessage);
        if (attempts >= maxAttempts) {
            channel.basicNack(deliveryTag, false, false);
            log.error("{} eventId={} {} outcome=DEAD_LETTERED after {} attempts: {}",
                    event.eventType(), event.eventId(), subject, attempts, ex.getMessage());
        } else {
            try {
                scheduleRetry(amqpMessage, attempts);
            } catch (RetryCopyReturnedException returned) {
                // The retry queue does not exist, and no retry can change
                // that: dead-letter the original rather than requeue it
                // forever.
                channel.basicNack(deliveryTag, false, false);
                log.error("{} eventId={} {} outcome=DEAD_LETTERED: {}; the event failed with: {}",
                        event.eventType(), event.eventId(), subject, returned.getMessage(), ex.getMessage());
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
                log.warn("{} eventId={} {} outcome=REQUEUED after {} ms: retry copy not accepted ({});"
                        + " the event failed with: {}",
                        event.eventType(), event.eventId(), subject, confirmTimeoutMs, publishFailure,
                        ex.getMessage());
                publishFailure.addSuppressed(ex);
                throw publishFailure;
            }
            channel.basicAck(deliveryTag, false);
            log.warn("{} eventId={} {} outcome=RETRY after attempt {}/{} failed: {}",
                    event.eventType(), event.eventId(), subject, attempts, maxAttempts, ex.getMessage());
        }
        // Rethrown for the container's failure log only, wrapped so the
        // delivery cannot be settled twice: a bare exception the error
        // handler classifies as fatal (e.g. a ClassCastException out of
        // the handler) would, in MANUAL mode, make the container reject
        // this already-settled tag (rejectManual) and close the channel
        // with PRECONDITION_FAILED (PR #271 review). AmqpException is
        // never classified fatal.
        throw new EventFailedException(event, subject, ex);
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
        rabbitTemplate.send(DEFAULT_EXCHANGE, retryQueue, message, correlation);

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

    /**
     * Carries the event handler's failure to the container's log after
     * the delivery is already settled. Deliberately an
     * {@link AmqpException}, which the container's error handler never
     * classifies fatal — a fatal classification would reject the
     * settled tag again and close the channel.
     */
    static final class EventFailedException extends AmqpException {

        EventFailedException(DomainEvent event, String subject, RuntimeException cause) {
            super(event.eventType() + " eventId=" + event.eventId() + " " + subject
                    + " failed; the delivery is settled", cause);
        }
    }
}
