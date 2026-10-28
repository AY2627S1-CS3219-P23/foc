/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: unit test of the declared topology (no broker): the three
 * queues and their failure legs, the four bound keys, and both
 * exchanges.
 * 2026-10-25, issue #235: request.dispute-resolved is the fifth bound
 * key.
 * 2026-10-28, Claude Code (Opus 5.5), issue #267: the user-events
 * queues, their two bindings and the exchange (team decision USR-1).
 * Same day, PR review: the exchange's every flag pinned, since
 * user-service declares it too and RabbitMQ refuses a mismatch.
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
	void bindsOnlyTheFiveEventsTheServiceActsOn() {
		assertThat(topology.getDeclarablesByType(Binding.class))
				.allSatisfy(binding -> {
					assertThat(binding.getExchange()).isEqualTo("request-events");
					assertThat(binding.getDestination()).isEqualTo("credit-service.request-events");
				})
				.extracting(Binding::getRoutingKey)
				.containsExactlyInAnyOrder("request.submitted", "request.completed", "request.cancelled",
						"request.expired", "request.dispute-resolved");
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
	void theUserEventsQueuesHaveTheSameLegsAndBindTheTwoUserEvents() {
		Declarables users = new RabbitMqTopology().userEventsTopology(2000);
		Map<String, Queue> byName = users.getDeclarablesByType(Queue.class).stream()
				.collect(Collectors.toMap(Queue::getName, Function.identity()));

		// exactly user-service's declaration: RabbitMQ refuses a second
		// declaration that differs (PRECONDITION_FAILED)
		assertThat(users.getDeclarablesByType(TopicExchange.class)).singleElement().satisfies(exchange -> {
			assertThat(exchange.getName()).isEqualTo("user-events");
			assertThat(exchange.getType()).isEqualTo(org.springframework.amqp.core.ExchangeTypes.TOPIC);
			assertThat(exchange.isDurable()).isTrue();
			assertThat(exchange.isAutoDelete()).isFalse();
			assertThat(exchange.isInternal()).isFalse();
			assertThat(exchange.getArguments()).isEmpty();
		});
		assertThat(users.getDeclarablesByType(Binding.class))
				.allSatisfy(binding -> {
					assertThat(binding.getExchange()).isEqualTo("user-events");
					assertThat(binding.getDestination()).isEqualTo("credit-service.user-events");
				})
				.extracting(Binding::getRoutingKey)
				.containsExactlyInAnyOrder("user.created", "user.purged");
		assertThat(byName.get("credit-service.user-events").getArguments())
				.containsEntry("x-dead-letter-routing-key", "credit-service.user-events.dlq");
		assertThat(byName.get("credit-service.user-events.retry").getArguments())
				.containsEntry("x-message-ttl", 2000)
				.containsEntry("x-dead-letter-routing-key", "credit-service.user-events");
		assertThat(byName.get("credit-service.user-events.dlq").getArguments())
				.doesNotContainKey("x-dead-letter-exchange");
	}

	@Test
	void deadLetterQueueIsTerminal() {
		Queue dlq = queues().get("credit-service.request-events.dlq");

		assertThat(dlq.isDurable()).isTrue();
		assertThat(dlq.getArguments()).doesNotContainKey("x-dead-letter-exchange");
	}
}
