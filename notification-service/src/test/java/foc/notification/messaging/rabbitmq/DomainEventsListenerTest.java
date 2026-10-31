/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, PR #276 review (@Sinnez1).
 * Scope: unit coverage for the listener's settle decisions the broker
 * integration tests cannot reach — a failed delivery whose consumer
 * queue is missing or has no RETRY_QUEUE_BY_WORK_QUEUE row must be
 * nacked with requeue (settled: in MANUAL mode nothing else would
 * settle it and it would pin a prefetch slot) and never published to a
 * guessed retry target; the mapped path must ack only after publishing
 * to that queue's own retry sibling.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import foc.contracts.events.rating.RatingSubmitted;
import foc.notification.service.EventProcessor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Settle decisions for the retry path (PR #276 review): the happy and
 * broker-visible failure legs run end-to-end in the two RabbitMQ
 * integration tests; these cases pin down the branches only a
 * malformed delivery or a future coding mistake can reach. Every
 * branch must settle the delivery exactly once — in MANUAL ack mode
 * the container nacks solely for
 * {@code ManualAckListenerExecutionRuntimeException}, so an unsettled
 * delivery holds a prefetch slot until the connection closes.
 */
class DomainEventsListenerTest {

	private static final long DELIVERY_TAG = 7L;

	private final EventProcessor eventProcessor = mock(EventProcessor.class);
	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final Channel channel = mock(Channel.class);
	private final DomainEventsListener listener =
			new DomainEventsListener(eventProcessor, rabbitTemplate, 3);

	@Test
	void deliveryWithoutConsumerQueueIsRequeuedNotRepublished() throws Exception {
		doThrow(new IllegalStateException("boom")).when(eventProcessor).process(any());
		Message delivery = new Message(new byte[0], new MessageProperties());

		assertThatThrownBy(() -> listener.onDomainEvent(rating(), delivery, channel, DELIVERY_TAG))
				.isInstanceOf(IllegalStateException.class);

		verify(channel).basicNack(DELIVERY_TAG, false, true);
		verifyNoMoreInteractions(channel);
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void unmappedConsumerQueueIsRequeuedNotRepublished() throws Exception {
		doThrow(new IllegalStateException("boom")).when(eventProcessor).process(any());
		MessageProperties properties = new MessageProperties();
		properties.setConsumerQueue("notification-service.unmapped-events");
		Message delivery = new Message(new byte[0], properties);

		assertThatThrownBy(() -> listener.onDomainEvent(rating(), delivery, channel, DELIVERY_TAG))
				.isInstanceOf(IllegalStateException.class);

		verify(channel).basicNack(DELIVERY_TAG, false, true);
		verifyNoMoreInteractions(channel);
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void mappedConsumerQueueRepublishesToItsOwnRetryQueueThenAcks() throws Exception {
		doThrow(new IllegalStateException("boom")).when(eventProcessor).process(any());
		RabbitOperations operations = mock(RabbitOperations.class);
		when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> invocation
				.<RabbitOperations.OperationsCallback<Object>>getArgument(0).doInRabbit(operations));
		MessageProperties properties = new MessageProperties();
		properties.setConsumerQueue(RabbitMqTopology.RATING_EVENTS_QUEUE);
		Message delivery = new Message(new byte[0], properties);

		assertThatThrownBy(() -> listener.onDomainEvent(rating(), delivery, channel, DELIVERY_TAG))
				.isInstanceOf(IllegalStateException.class);

		verify(operations).send("", RabbitMqTopology.RATING_EVENTS_RETRY_QUEUE, delivery);
		verify(channel).basicAck(DELIVERY_TAG, false);
		verifyNoMoreInteractions(channel);
	}

	private static RatingSubmitted rating() {
		return new RatingSubmitted("e-rat-unit-1", Instant.parse("2026-09-19T09:30:00Z"),
				"rating-service", "c-unit", List.of("usr-cou-2002"), "rat-20260919-0001",
				"req-20260919-0042", "usr-req-1001", "usr-cou-2002", 4);
	}
}
