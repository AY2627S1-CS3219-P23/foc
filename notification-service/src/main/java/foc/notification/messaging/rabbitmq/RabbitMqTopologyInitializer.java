/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: startup topology provisioning for issue #62 per team decision
 * D12 (docs/notification-service.md). Same day, on the author's
 * cleanup decision: declares all Declarables groups generically
 * instead of qualifier-injecting each topology bean.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.rabbitmq;

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
 * Provisions the broker topology at application startup (D12:
 * app-declared via Spring AMQP), failing fast if the broker is
 * unreachable or a declaration conflicts — the listener container
 * alone would only retry in the background.
 *
 * <p>Topology-agnostic: it declares whatever {@link Declarables}
 * groups the context defines ({@link RabbitMqTopology}), exchanges
 * first, then queues, then bindings — so declaration order never
 * depends on how a group lists its members, and new topology needs no
 * change here.
 *
 * <p>Disabled in tests via {@code notification.rabbitmq.provision-on-startup=false}
 * so {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "notification.rabbitmq.provision-on-startup", havingValue = "true", matchIfMissing = true)
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
