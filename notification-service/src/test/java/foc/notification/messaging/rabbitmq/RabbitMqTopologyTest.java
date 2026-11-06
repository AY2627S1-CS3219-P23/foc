/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-06.
 * Scope: issue #48 (DP10): the work queues' single-active-consumer
 * argument (team decision, Leong Wei Zhi via options Q&A), checked on the
 * declarations themselves — no broker. The consumer-less retry and
 * dead-letter queues keep their arguments as they were.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

class RabbitMqTopologyTest {

	private static final String SAC = "x-single-active-consumer";

	private final RabbitMqTopology topology = new RabbitMqTopology();

	private static Map<String, Queue> queues(Declarables group) {
		return group.getDeclarablesByType(Queue.class).stream()
				.collect(Collectors.toMap(Queue::getName, queue -> queue));
	}

	@Test
	void theRequestWorkQueueHasASingleActiveConsumer() {
		Map<String, Queue> queues = queues(topology.requestEventsTopology(500));

		assertThat(queues.get(RabbitMqTopology.REQUEST_EVENTS_QUEUE).getArguments()).containsEntry(SAC, true);
		assertThat(queues.get(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE).getArguments()).doesNotContainKey(SAC);
		assertThat(queues.get(RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE).getArguments())
				.doesNotContainKey(SAC);
	}

	@Test
	void theRatingWorkQueueHasASingleActiveConsumer() {
		Map<String, Queue> queues = queues(topology.ratingEventsTopology(500));

		assertThat(queues.get(RabbitMqTopology.RATING_EVENTS_QUEUE).getArguments()).containsEntry(SAC, true);
		assertThat(queues.get(RabbitMqTopology.RATING_EVENTS_RETRY_QUEUE).getArguments()).doesNotContainKey(SAC);
		assertThat(queues.get(RabbitMqTopology.RATING_EVENTS_DEAD_LETTER_QUEUE).getArguments())
				.doesNotContainKey(SAC);
	}

	// the dead-letter wiring is unchanged by the new argument
	@Test
	void theWorkQueuesStillDeadLetterToTheirDlq() {
		Queue requestWork = queues(topology.requestEventsTopology(500)).get(RabbitMqTopology.REQUEST_EVENTS_QUEUE);

		assertThat(requestWork.getArguments())
				.containsEntry("x-dead-letter-exchange", "")
				.containsEntry("x-dead-letter-routing-key", RabbitMqTopology.REQUEST_EVENTS_DEAD_LETTER_QUEUE)
				.hasSize(3);
	}
}
