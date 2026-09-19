/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: AMQP listener adapter for issue #64 per team decisions D11
 * (thin adapter, business logic behind the EventProcessor port) and
 * the author's ack/nack decisions: literal manual ack after the
 * processor's transaction commits (NFR1.1); nack with requeue on
 * failure, with backoff/attempt limits deferred to issue #65.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import foc.contracts.events.EventEnvelope;
import foc.notification.service.EventProcessor;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Thin inbound adapter (D11): deserialization happens in the message
 * converter before this method runs; this class only hands the
 * envelope to the {@link EventProcessor} port and acks/nacks on the
 * result. Manual ack after the processor returns means the DB commit
 * strictly precedes the ack — a crash in between causes redelivery,
 * never loss, and the processor's dedupe absorbs the redelivered copy
 * (NFR1.1).
 *
 * <p>Failure handling until #65's retry/DLQ topology: processing
 * failures are nacked with requeue (immediate redelivery, nothing
 * lost); malformed messages never reach this method — the container's
 * default error handler rejects fatal conversion failures without
 * requeue. Backoff schedule and attempt limits are a pending team
 * decision owned by issue #65 — deliberately not implemented here.
 */
@Component
class OrderEventsListener {

	private final EventProcessor eventProcessor;

	OrderEventsListener(EventProcessor eventProcessor) {
		this.eventProcessor = eventProcessor;
	}

	@RabbitListener(queues = RabbitMqTopology.WORK_QUEUE)
	void onOrderEvent(EventEnvelope envelope, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		try {
			eventProcessor.process(envelope);
		} catch (RuntimeException ex) {
			channel.basicNack(deliveryTag, false, true);
			throw ex;
		}
		channel.basicAck(deliveryTag, false);
	}
}
