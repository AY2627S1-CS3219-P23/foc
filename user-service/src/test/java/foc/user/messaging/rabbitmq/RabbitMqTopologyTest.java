/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267 review: the user-events exchange is declared exactly as
       credit-service declares it (topic, durable, not auto-delete, no
       arguments). RabbitMQ refuses a second declaration that differs, so
       credit-service's RabbitMqTopologyTest pins the same flags.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqTopologyTest {

    private final Declarables topology = new RabbitMqTopology().userEventsTopology();

    @Test
    void declaresOnlyTheUserEventsExchangeWithCreditServicesFlags() {
        assertThat(topology.getDeclarablesByType(TopicExchange.class)).singleElement().satisfies(exchange -> {
            assertThat(exchange.getName()).isEqualTo("user-events");
            assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();
            assertThat(exchange.isInternal()).isFalse();
            assertThat(exchange.getArguments()).isEmpty();
        });
        // the queue and its bindings are credit-service's
        assertThat(topology.getDeclarablesByType(Queue.class)).isEmpty();
        assertThat(topology.getDeclarablesByType(Binding.class)).isEmpty();
    }
}
