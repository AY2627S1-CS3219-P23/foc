/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: broker topology declaration for issue #62 per team decisions
 * D1, D8, D11-D15 (docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import foc.contracts.events.EventContracts;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core happy-path topology (D12-D14): the durable {@code order-events}
 * fanout exchange the Order Service publishes to, and this service's
 * durable work queue bound to it.
 *
 * <p>Durability is opt-in in RabbitMQ (D8, Notif NFR1.2): the exchange
 * and queue are declared durable here, and publishers must send
 * messages with delivery mode PERSISTENT — Spring AMQP's
 * {@code RabbitTemplate} default — or queued events will not survive a
 * broker restart.
 *
 * <p>Broker-specific code stays in this package (D11); business logic
 * must not import broker types.
 */
@Configuration
public class RabbitMqTopology {

	/**
	 * This service's work queue; consumer-prefixed per the team naming
	 * convention (D13) and service-private, so it lives here rather
	 * than in foc-contracts (D15). Retry/DLQ counterparts follow under
	 * issue #65.
	 */
	public static final String WORK_QUEUE = "notification-service.order-events";

	@Bean
	FanoutExchange orderEventsExchange() {
		return new FanoutExchange(EventContracts.ORDER_EVENTS_EXCHANGE, true, false);
	}

	@Bean
	Queue orderEventsWorkQueue() {
		return QueueBuilder.durable(WORK_QUEUE).build();
	}

	@Bean
	Binding orderEventsBinding(FanoutExchange orderEventsExchange, Queue orderEventsWorkQueue) {
		return BindingBuilder.bind(orderEventsWorkQueue).to(orderEventsExchange);
	}

}
