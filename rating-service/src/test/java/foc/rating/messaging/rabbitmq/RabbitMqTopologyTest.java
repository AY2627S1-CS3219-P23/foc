/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #251.
 * Scope: unit test of the declared topology (no broker), a trimmed copy
 * of credit-service's RabbitMqTopologyTest: the three queues and their
 * failure legs, the one bound key, the exchange's every flag pinned —
 * other services declare request-events too and RabbitMQ refuses a
 * mismatch (PRECONDITION_FAILED).
 * Author review: [pending]
 */
package foc.rating.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqTopologyTest {

    private final Declarables topology = new RabbitMqTopology().requestEventsTopology(2000);

    private Map<String, Queue> queues() {
        return topology.getDeclarablesByType(Queue.class).stream()
                .collect(Collectors.toMap(Queue::getName, Function.identity()));
    }

    @Test
    void declaresTheRequestEventsExchangeExactlyAsEveryOtherDeclarer() {
        assertThat(topology.getDeclarablesByType(TopicExchange.class)).singleElement().satisfies(exchange -> {
            assertThat(exchange.getName()).isEqualTo("request-events");
            assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();
            assertThat(exchange.isInternal()).isFalse();
            assertThat(exchange.getArguments()).isEmpty();
        });
    }

    @Test
    void bindsOnlyTheOneEventTheServiceActsOn() {
        assertThat(topology.getDeclarablesByType(Binding.class))
                .allSatisfy(binding -> {
                    assertThat(binding.getExchange()).isEqualTo("request-events");
                    assertThat(binding.getDestination()).isEqualTo("rating-service.request-events");
                })
                .extracting(Binding::getRoutingKey)
                .containsExactly("request.completed");
    }

    @Test
    void workQueueDeadLettersToTheDlq() {
        Queue work = queues().get("rating-service.request-events");

        assertThat(work.isDurable()).isTrue();
        assertThat(work.getArguments())
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "rating-service.request-events.dlq");
    }

    @Test
    void retryQueueReturnsToTheWorkQueueAfterTheDelay() {
        Queue retry = queues().get("rating-service.request-events.retry");

        assertThat(retry.isDurable()).isTrue();
        assertThat(retry.getArguments())
                .containsEntry("x-message-ttl", 2000)
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "rating-service.request-events");
    }

    @Test
    void deadLetterQueueIsTerminal() {
        Queue dlq = queues().get("rating-service.request-events.dlq");

        assertThat(dlq.isDurable()).isTrue();
        assertThat(dlq.getArguments()).doesNotContainKey("x-dead-letter-exchange");
    }
}
