/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 (team decision USR-2): a copy of order-service's
       RabbitEventPublisher (mandatory flag, correlated publisher confirm,
       5 s confirm wait, which failures stop a poll), sending to the
       user-events exchange instead; copied, not shared.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.messaging.rabbitmq;

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
import foc.user.entity.OutboxEvent;
import foc.user.service.EventPublisher;

/**
 * Sends a stored event to the {@code user-events} exchange, routed by
 * its event type, and waits for the broker's answer. The stored payload
 * is the message body as it is; nothing is converted here.
 *
 * <p>Needs {@code spring.rabbitmq.publisher-confirm-type: correlated},
 * {@code publisher-returns} and {@code template.mandatory} (set in
 * application.yaml): the broker returns an event no queue took before
 * it confirms it, so by the time the confirm arrives the return, if
 * any, is already on the {@link CorrelationData}. No returns callback
 * is registered, so the shared template's single slot stays free.
 */
@Component
class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    RabbitEventPublisher(RabbitTemplate rabbitTemplate,
            @Value("${user.outbox.publisher.confirm-timeout-ms}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Override
    public Result publish(OutboxEvent event) {
        String eventId = event.getEventId().toString();
        CorrelationData correlation = new CorrelationData(eventId);
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(eventId)
                // survives a broker restart once a durable queue holds it
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        try {
            rabbitTemplate.send(EventContracts.USER_EVENTS_EXCHANGE, event.getEventType(), message, correlation);
        } catch (AmqpException ex) {
            // the event never left: no connection or no channel
            log.warn("Event {} ({}) not published: broker unreachable: {}",
                    eventId, event.getEventType(), ex.getMessage());
            return Result.BROKER_UNAVAILABLE;
        }

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // a broker that does not answer would cost every other event
            // the same wait
            log.warn("Event {} ({}) not confirmed: no confirm from the broker within {} ms",
                    eventId, event.getEventType(), confirmTimeoutMs);
            return Result.BROKER_UNAVAILABLE;
        } catch (ExecutionException ex) {
            log.warn("Event {} ({}) not confirmed: waiting for the confirm failed: {}",
                    eventId, event.getEventType(), ex.getMessage());
            return Result.BROKER_UNAVAILABLE;
        } catch (InterruptedException ex) {
            // the service is shutting down: stop the poll, the event
            // stays unsent for the next start
            Thread.currentThread().interrupt();
            return Result.BROKER_UNAVAILABLE;
        }

        if (!confirm.ack()) {
            log.warn("Event {} ({}) not confirmed: nacked{}", eventId, event.getEventType(),
                    confirm.reason() == null ? "" : ": " + confirm.reason());
            return Result.BROKER_UNAVAILABLE;
        }
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            // expected until credit-service has bound its queue (USR-9)
            log.warn("Event {} ({}) stays unsent: returned as unroutable ({} {}); is a queue bound to {}?",
                    eventId, event.getEventType(), returned.getReplyCode(), returned.getReplyText(),
                    EventContracts.USER_EVENTS_EXCHANGE);
            return Result.NOT_SENT;
        }
        return Result.SENT;
    }
}
