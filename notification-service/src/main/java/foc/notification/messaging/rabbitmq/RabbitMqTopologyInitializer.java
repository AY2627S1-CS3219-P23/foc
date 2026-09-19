/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: startup topology provisioning for issue #62 per team decision
 * D12 (docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provisions the broker topology at application startup (D12:
 * app-declared via Spring AMQP). Spring only declares the beans in
 * {@link RabbitMqTopology} when a connection is first opened, and this
 * service has no publishers or listeners yet, so this runner forces the
 * declaration by opening one — failing fast if the broker is
 * unreachable.
 *
 * <p>Disabled in tests via {@code notification.rabbitmq.provision-on-startup=false}
 * so {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "notification.rabbitmq.provision-on-startup", havingValue = "true", matchIfMissing = true)
class RabbitMqTopologyInitializer implements ApplicationRunner {

	private final AmqpAdmin amqpAdmin;
	private final FanoutExchange orderEventsExchange;
	private final Queue orderEventsWorkQueue;
	private final Binding orderEventsBinding;

	RabbitMqTopologyInitializer(AmqpAdmin amqpAdmin, FanoutExchange orderEventsExchange,
			Queue orderEventsWorkQueue, Binding orderEventsBinding) {
		this.amqpAdmin = amqpAdmin;
		this.orderEventsExchange = orderEventsExchange;
		this.orderEventsWorkQueue = orderEventsWorkQueue;
		this.orderEventsBinding = orderEventsBinding;
	}

	@Override
	public void run(ApplicationArguments args) {
		amqpAdmin.declareExchange(orderEventsExchange);
		amqpAdmin.declareQueue(orderEventsWorkQueue);
		amqpAdmin.declareBinding(orderEventsBinding);
	}

}
