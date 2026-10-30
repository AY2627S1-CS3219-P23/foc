/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: startup topology provisioning, a copy of credit-service's
 * RabbitMqTopologyInitializer (itself a copy of notification-service's,
 * Claude Code (Fable 5), 2026-09-19, reviewed by Leong Wei Zhi) with
 * the package and the property name changed. Copied, not shared, as
 * the team does for everything but foc-contracts.
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

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
 * <p>Off in tests via {@code rating.rabbitmq.provision-on-startup=false}
 * so {@code ./mvnw test} needs no running broker.
 */
@Component
@ConditionalOnProperty(name = "rating.rabbitmq.provision-on-startup", havingValue = "true", matchIfMissing = true)
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
