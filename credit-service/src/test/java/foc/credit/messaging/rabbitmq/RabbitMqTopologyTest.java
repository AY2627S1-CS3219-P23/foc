/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: unit test of the declared topology (no broker): the three
 * queues and their failure legs, the four bound keys, and both
 * exchanges.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqTopologyTest {

	private final Declarables topology = new RabbitMqTopology().sagaTopology(2000);

	private Map<String, Queue> queues() {
		return topology.getDeclarablesByType(Queue.class).stream()
				.collect(Collectors.toMap(Queue::getName, Function.identity()));
	}

	@Test
	void declaresBothExchangesAsDurableTopics() {
		assertThat(topology.getDeclarablesByType(TopicExchange.class))
				.extracting(TopicExchange::getName, TopicExchange::isDurable, TopicExchange::isAutoDelete)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("request-events", true, false),
						org.assertj.core.groups.Tuple.tuple("credit-events", true, false));
	}

	@Test
	void bindsOnlyTheFourEventsTheServiceActsOn() {
		assertThat(topology.getDeclarablesByType(Binding.class))
				.allSatisfy(binding -> {
					assertThat(binding.getExchange()).isEqualTo("request-events");
					assertThat(binding.getDestination()).isEqualTo("credit-service.request-events");
				})
				.extracting(Binding::getRoutingKey)
				.containsExactlyInAnyOrder(
						"request.submitted", "request.completed", "request.cancelled", "request.expired");
	}

	@Test
	void workQueueDeadLettersToTheDlq() {
		Queue work = queues().get("credit-service.request-events");

		assertThat(work.isDurable()).isTrue();
		assertThat(work.getArguments())
				.containsEntry("x-dead-letter-exchange", "")
				.containsEntry("x-dead-letter-routing-key", "credit-service.request-events.dlq");
	}

	@Test
	void retryQueueReturnsToTheWorkQueueAfterTheDelay() {
		Queue retry = queues().get("credit-service.request-events.retry");

		assertThat(retry.isDurable()).isTrue();
		assertThat(retry.getArguments())
				.containsEntry("x-message-ttl", 2000)
				.containsEntry("x-dead-letter-exchange", "")
				.containsEntry("x-dead-letter-routing-key", "credit-service.request-events");
	}

	@Test
	void deadLetterQueueIsTerminal() {
		Queue dlq = queues().get("credit-service.request-events.dlq");

		assertThat(dlq.isDurable()).isTrue();
		assertThat(dlq.getArguments()).doesNotContainKey("x-dead-letter-exchange");
	}
}
