/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: broker topology declaration for issue #62 per team decisions
 * D1, D8, D11-D15 (docs/notification-service.md).
 * Same day, Method-B refactor: fanout exchange replaced by a topic
 * exchange with an "order.#" binding (author decision D16, supersedes
 * D14); then, on the author's cleanup decision, the per-domain
 * declarations were grouped into one Declarables bean so topology
 * additions no longer touch the initializer.
 * 2026-09-20, issue #65: retry queue and DLQ added per team decisions
 * D6/D7 (values decided 2026-09-19: 10 s TTL, 3 attempts) and the
 * author's decisions of 2026-09-20: the default exchange serves as
 * the dead-letter exchange on both legs, and unconvertible messages
 * dead-letter on first rejection.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import foc.contracts.events.EventContracts;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Order-domain topology (D12, D13, D16): the durable
 * {@code order-events} <strong>topic</strong> exchange the Order
 * Service publishes to with routing keys from the shared
 * {@code EventTypeRegistry} (D18), and this service's durable work
 * queue bound with {@value #ORDER_EVENTS_BINDING_PATTERN} — this
 * service notifies on <em>every</em> order event (F1.1, F1.2), and a
 * new event type ships via a contracts release anyway, so the binding
 * never changes.
 *
 * <p>Failure topology (D6/D7, F2.2/F2.3): the work queue dead-letters
 * to the durable DLQ, and the durable retry queue parks a failed
 * event for {@code notification.rabbitmq.retry.ttl-ms} before its own
 * dead-letter leg returns it to the work queue for another attempt.
 * Both legs use the <em>default exchange</em> as their dead-letter
 * exchange (author decision, 2026-09-20): zero extra declarables, at
 * the cost that the routing key is rewritten to the target queue name
 * in transit — nothing is lost, since the body's {@code eventType} is
 * the original routing key (D18) and the broker's {@code x-death}
 * header records the original keys. Who routes where: the listener
 * republishes to the retry queue while attempts remain and nacks
 * without requeue when they are exhausted; a message the converter
 * cannot even convert (unknown eventType, malformed body) is rejected
 * by the container on first delivery and dead-letters straight to the
 * DLQ — retrying it could never succeed (author decision, 2026-09-20).
 *
 * <p>Each domain's declarations are grouped into one
 * {@link Declarables} bean; {@link RabbitMqTopologyInitializer}
 * declares every such group generically, so adding topology (a second
 * domain) touches only this class.
 *
 * <p>Durability is opt-in in RabbitMQ (D8, Notif NFR1.2): the exchange
 * and queues are declared durable here, and publishers must send
 * messages with delivery mode PERSISTENT — Spring AMQP's
 * {@code RabbitTemplate} default — or queued events will not survive a
 * broker restart.
 *
 * <p>Migration note: queue/exchange arguments are immutable once
 * declared — a broker volume holding the pre-refactor fanout exchange,
 * or the pre-#65 work queue (no dead-letter arguments), or a retry
 * queue declared with a different TTL, makes the declaration fail
 * ({@code PRECONDITION_FAILED}); reset the volume once — see the
 * service README.
 *
 * <p>Broker-specific code stays in this package (D11); business logic
 * must not import broker types.
 */
@Configuration
public class RabbitMqTopology {

	/**
	 * This service's work queue for the order domain; consumer-prefixed
	 * per the team naming convention (D13) and service-private, so it
	 * lives here rather than in foc-contracts (D15).
	 */
	public static final String ORDER_EVENTS_QUEUE = "notification-service.order-events";

	/**
	 * TTL/delay queue (D6, F2.2): the listener parks a failed event
	 * here; on TTL expiry the broker returns it to the work queue.
	 */
	public static final String ORDER_EVENTS_RETRY_QUEUE = ORDER_EVENTS_QUEUE + ".retry";

	/**
	 * Terminal queue (D7, F2.3): events that exhausted their attempts
	 * or never converted, kept for inspection and manual re-publish.
	 */
	public static final String ORDER_EVENTS_DEAD_LETTER_QUEUE = ORDER_EVENTS_QUEUE + ".dlq";

	/** All order-domain events, current and future (D16). */
	static final String ORDER_EVENTS_BINDING_PATTERN = "order.#";

	/**
	 * The broker's built-in default exchange: routes by queue name, so
	 * a dead-letter leg needs no declared exchange or binding.
	 */
	private static final String DEFAULT_EXCHANGE = "";

	@Bean
	Declarables orderEventsTopology(
			@Value("${notification.rabbitmq.retry.ttl-ms}") int retryTtlMs) {
		TopicExchange exchange = new TopicExchange(EventContracts.ORDER_EVENTS_EXCHANGE, true, false);
		Queue workQueue = QueueBuilder.durable(ORDER_EVENTS_QUEUE)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(ORDER_EVENTS_DEAD_LETTER_QUEUE)
				.build();
		Queue retryQueue = QueueBuilder.durable(ORDER_EVENTS_RETRY_QUEUE)
				.ttl(retryTtlMs)
				.deadLetterExchange(DEFAULT_EXCHANGE)
				.deadLetterRoutingKey(ORDER_EVENTS_QUEUE)
				.build();
		Queue deadLetterQueue = QueueBuilder.durable(ORDER_EVENTS_DEAD_LETTER_QUEUE).build();
		Binding binding = BindingBuilder.bind(workQueue).to(exchange)
				.with(ORDER_EVENTS_BINDING_PATTERN);
		return new Declarables(exchange, workQueue, retryQueue, deadLetterQueue, binding);
	}

}
