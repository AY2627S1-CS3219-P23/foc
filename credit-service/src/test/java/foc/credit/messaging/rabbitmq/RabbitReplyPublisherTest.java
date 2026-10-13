/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-12, issue #170.
 * Scope: how the reply publisher reads each answer from the broker,
 * with the RabbitTemplate mocked: only a confirm with no return is
 * SENT, only a return is NOT_SENT, and everything else means the broker
 * is in trouble. The last two tests run a poll of three replies through
 * it, for the team's revised decision that a nack or a missing confirm
 * stops the poll.
 * 2026-10-13, Claude Code (Opus 5.5), PR #188 review (Leong Wei Zhi):
 * the publisher registers no returns callback on the shared template.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Limit;

import foc.contracts.events.core.EventContracts;
import foc.credit.entity.OutboxEvent;
import foc.credit.repository.OutboxEventRepository;
import foc.credit.service.OutboxPublisher;
import foc.credit.service.ReplyPublisher.Result;

class RabbitReplyPublisherTest {

	private static final String PAYLOAD = "{\"eventType\":\"credit.reserved\",\"requestId\":\"r-1\"}";
	private static final int BATCH_SIZE = 100;

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	// a short confirm wait, so the timeout cases do not hold the build up
	private final RabbitReplyPublisher publisher = new RabbitReplyPublisher(rabbitTemplate, 200);
	private final OutboxEvent reply = reply();

	@Test
	void aConfirmedReplyIsSentAsStoredToTheCreditEventsExchange() {
		brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));

		assertThat(publisher.publish(reply)).isEqualTo(Result.SENT);

		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
		verify(rabbitTemplate).send(eq(EventContracts.CREDIT_EVENTS_EXCHANGE), eq("credit.reserved"),
				message.capture(), correlation.capture());
		assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
		MessageProperties properties = message.getValue().getMessageProperties();
		assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
		assertThat(properties.getMessageId()).isEqualTo(reply.getEventId().toString());
		assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(correlation.getValue().getId()).isEqualTo(reply.getEventId().toString());
	}

	@Test
	void aConfirmedButReturnedReplyIsNotSent() {
		brokerAnswers(RabbitReplyPublisherTest::returned);

		assertThat(publisher.publish(reply)).isEqualTo(Result.NOT_SENT);
	}

	@Test
	void aNackMeansTheBrokerIsUnavailable() {
		brokerAnswers(RabbitReplyPublisherTest::nacked);

		assertThat(publisher.publish(reply)).isEqualTo(Result.BROKER_UNAVAILABLE);
	}

	@Test
	void aNackFromAClosedChannelMeansTheSame() {
		// the client nacks a pending confirm itself, with a reason, when
		// the channel closes under it; it is read no differently
		brokerAnswers(correlation -> correlation.getFuture()
				.complete(new CorrelationData.Confirm(false, "Channel closed by application")));

		assertThat(publisher.publish(reply)).isEqualTo(Result.BROKER_UNAVAILABLE);
	}

	@Test
	void aMissingConfirmMeansTheBrokerIsUnavailable() {
		brokerAnswers(RabbitReplyPublisherTest::neverConfirmed);

		assertThat(publisher.publish(reply)).isEqualTo(Result.BROKER_UNAVAILABLE);
	}

	@Test
	void anUnreachableBrokerIsUnavailable() {
		doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
				.when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

		assertThat(publisher.publish(reply)).isEqualTo(Result.BROKER_UNAVAILABLE);
	}

	@Test
	void aConfirmTimeoutStopsThePoll() {
		assertThePollStopsAtTheSecondReplyWhenItIs(RabbitReplyPublisherTest::neverConfirmed);
	}

	// the shared template takes one returns callback; a return is read
	// from the CorrelationData without it (PR #188 review)
	@Test
	void noReturnsCallbackIsRegisteredOnTheSharedTemplate() {
		verify(rabbitTemplate, never()).setReturnsCallback(any());
	}

	@Test
	void aNackStopsThePoll() {
		assertThePollStopsAtTheSecondReplyWhenItIs(RabbitReplyPublisherTest::nacked);
	}

	@Test
	void aReturnedReplyDoesNotStopThePoll() {
		OutboxEvent first = reply();
		OutboxEvent second = reply();
		OutboxEvent third = reply();
		OutboxPublisher poll = pollOver(first, second, third);
		brokerAnswers(correlation -> {
			if (correlation.getId().equals(second.getEventId().toString())) {
				returned(correlation);
			} else {
				confirmed(correlation);
			}
		});

		poll.publishUnsent();

		assertThat(first.getSentAt()).isNotNull();
		assertThat(second.getSentAt()).isNull();
		assertThat(third.getSentAt()).isNotNull();
	}

	// three unsent replies; the first is confirmed, the second gets the
	// given answer: the third must never be published, and the second
	// and third stay unsent for the next poll
	private void assertThePollStopsAtTheSecondReplyWhenItIs(Consumer<CorrelationData> trouble) {
		OutboxEvent first = reply();
		OutboxEvent second = reply();
		OutboxEvent third = reply();
		OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);
		OutboxPublisher poll = pollOver(outboxEvents, first, second, third);
		brokerAnswers(correlation -> {
			if (correlation.getId().equals(second.getEventId().toString())) {
				trouble.accept(correlation);
			} else {
				confirmed(correlation);
			}
		});

		poll.publishUnsent();

		assertThat(first.getSentAt()).isNotNull();
		assertThat(second.getSentAt()).isNull();
		assertThat(third.getSentAt()).isNull();
		verify(rabbitTemplate, times(2)).send(any(), any(), any(Message.class), any(CorrelationData.class));
		verify(outboxEvents).save(first);
		verify(outboxEvents, never()).save(second);
		verify(outboxEvents, never()).save(third);
	}

	private OutboxPublisher pollOver(OutboxEvent... unsent) {
		return pollOver(mock(OutboxEventRepository.class), unsent);
	}

	private OutboxPublisher pollOver(OutboxEventRepository outboxEvents, OutboxEvent... unsent) {
		when(outboxEvents.findDue(any(Instant.class), eq(Limit.of(BATCH_SIZE)))).thenReturn(List.of(unsent));
		return new OutboxPublisher(outboxEvents, publisher, BATCH_SIZE, 30_000);
	}

	private void brokerAnswers(Consumer<CorrelationData> answer) {
		doAnswer(invocation -> {
			answer.accept(invocation.getArgument(3));
			return null;
		}).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
	}

	private static void confirmed(CorrelationData correlation) {
		correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
	}

	private static void nacked(CorrelationData correlation) {
		correlation.getFuture().complete(new CorrelationData.Confirm(false, null));
	}

	private static void neverConfirmed(CorrelationData correlation) {
	}

	// the broker returns a reply no queue took, then confirms it
	private static void returned(CorrelationData correlation) {
		correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
				EventContracts.CREDIT_EVENTS_EXCHANGE, "credit.reserved"));
		confirmed(correlation);
	}

	private static OutboxEvent reply() {
		return new OutboxEvent(UUID.randomUUID(), "credit.reserved", PAYLOAD, Instant.now());
	}
}
