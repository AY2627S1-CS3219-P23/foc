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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core happy-path topology (D12, D13, D16): the durable
 * {@code order-events} <strong>topic</strong> exchange the Order
 * Service publishes to with routing keys from the shared
 * {@code EventTypeRegistry} (D18), and this service's durable work
 * queue bound with {@value #ORDER_EVENTS_BINDING_PATTERN} — this
 * service notifies on <em>every</em> order event (F1.1, F1.2), and a
 * new event type ships via a contracts release anyway, so the binding
 * never changes.
 *
 * <p>Each domain's declarations are grouped into one
 * {@link Declarables} bean; {@link RabbitMqTopologyInitializer}
 * declares every such group generically, so adding topology (a second
 * domain, #65's retry/DLQ queues) touches only this class.
 *
 * <p>Durability is opt-in in RabbitMQ (D8, Notif NFR1.2): the exchange
 * and queue are declared durable here, and publishers must send
 * messages with delivery mode PERSISTENT — Spring AMQP's
 * {@code RabbitTemplate} default — or queued events will not survive a
 * broker restart.
 *
 * <p>Migration note: a broker volume that still holds the pre-refactor
 * fanout exchange makes the topic declaration fail
 * ({@code PRECONDITION_FAILED}); reset it once — see the service
 * README.
 *
 * <p>Broker-specific code stays in this package (D11); business logic
 * must not import broker types.
 */
@Configuration
public class RabbitMqTopology {

	/**
	 * This service's work queue for the order domain; consumer-prefixed
	 * per the team naming convention (D13) and service-private, so it
	 * lives here rather than in foc-contracts (D15). Retry/DLQ
	 * counterparts (ORDER_EVENTS_RETRY_QUEUE,
	 * ORDER_EVENTS_DEAD_LETTER_QUEUE) follow under issue #65.
	 */
	public static final String ORDER_EVENTS_QUEUE = "notification-service.order-events";

	/** All order-domain events, current and future (D16). */
	static final String ORDER_EVENTS_BINDING_PATTERN = "order.#";

	@Bean
	Declarables orderEventsTopology() {
		TopicExchange exchange = new TopicExchange(EventContracts.ORDER_EVENTS_EXCHANGE, true, false);
		Queue workQueue = QueueBuilder.durable(ORDER_EVENTS_QUEUE).build();
		Binding binding = BindingBuilder.bind(workQueue).to(exchange)
				.with(ORDER_EVENTS_BINDING_PATTERN);
		return new Declarables(exchange, workQueue, binding);
	}

}
