/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: startup topology provisioning, a copy of notification-service's
 * RabbitMqTopologyInitializer (Claude Code (Fable 5), 2026-09-19,
 * reviewed by Leong Wei Zhi) with the package and the property name
 * changed. Copied, not shared, by the author's decision.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import java.util.List;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Declares the broker topology at application startup, failing fast if
 * the broker is unreachable or a declaration conflicts — the listener
 * container alone would only retry in the background.
 *
 * <p>Declares whatever {@link Declarables} groups the context defines
 * ({@link RabbitMqTopology}): exchanges first, then queues, then
 * bindings.
 *
 * <p>Off in tests via {@code credit.rabbitmq.provision-on-startup=false}
 * so {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "credit.rabbitmq.provision-on-startup", havingValue = "true", matchIfMissing = true)
class RabbitMqTopologyInitializer implements ApplicationRunner {

	private final AmqpAdmin amqpAdmin;
	private final List<Declarables> topologies;

	RabbitMqTopologyInitializer(AmqpAdmin amqpAdmin, List<Declarables> topologies) {
		this.amqpAdmin = amqpAdmin;
		this.topologies = topologies;
	}

	@Override
	public void run(ApplicationArguments args) {
		declareAll(Exchange.class, amqpAdmin::declareExchange);
		declareAll(Queue.class, amqpAdmin::declareQueue);
		declareAll(Binding.class, amqpAdmin::declareBinding);
	}

	private <T extends Declarable> void declareAll(Class<T> type, java.util.function.Consumer<T> declare) {
		topologies.stream()
				.flatMap(group -> group.getDeclarablesByType(type).stream())
				.forEach(declare);
	}

}
