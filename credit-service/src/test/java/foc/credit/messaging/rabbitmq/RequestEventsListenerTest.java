/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-14, issue #171.
 * Scope: how the request event listener settles each delivery, with
 * the handler, the channel and the RabbitTemplate mocked: HANDLED is
 * acknowledged, INVALID_STATE dead-lettered at once, a failure
 * republished to the retry queue until the attempt cap, and the
 * original never acknowledged unless the retry copy was confirmed.
 * Same day, team decision on review: a retry copy the broker does not
 * accept requeues the original.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import com.rabbitmq.client.Channel;

import foc.contracts.events.request.RequestCompleted;
import foc.credit.service.RequestEventHandler;
import foc.credit.service.RequestEventHandler.Outcome;
import foc.credit.service.ReservationNotFoundException;

class RequestEventsListenerTest {

	private static final int MAX_ATTEMPTS = 5;
	private static final long TAG = 7L;

	private final RequestEventHandler handler = mock(RequestEventHandler.class);
	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final Channel channel = mock(Channel.class);
	private final RequestEventsListener listener =
			new RequestEventsListener(handler, rabbitTemplate, MAX_ATTEMPTS);
	private final RequestCompleted event = completed();

	@Test
	void aHandledEventIsAcknowledged() throws Exception {
		when(handler.handle(event)).thenReturn(Outcome.HANDLED);

		listener.onRequestEvent(event, delivery(null), channel, TAG);

		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void anEventThatConflictsWithItsRecordIsDeadLetteredWithoutARetry() throws Exception {
		when(handler.handle(event)).thenReturn(Outcome.INVALID_STATE);

		listener.onRequestEvent(event, delivery(null), channel, TAG);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void aFirstFailureIsRepublishedToTheRetryQueueAndTheOriginalAcknowledged() throws Exception {
		when(handler.handle(event)).thenThrow(new DataIntegrityViolationException("duplicate request_ref"));
		brokerAnswers(RequestEventsListenerTest::confirmed);
		Message delivery = delivery(null);

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery, channel, TAG))
				.isInstanceOf(DataIntegrityViolationException.class);

		ArgumentCaptor<Message> copy = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq(""), eq(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE),
				copy.capture(), any(CorrelationData.class));
		MessageProperties copied = copy.getValue().getMessageProperties();
		assertThat((Object) copied.getHeader(RequestEventsListener.RETRY_ATTEMPTS_HEADER)).isEqualTo(1L);
		assertThat(copied.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void aCompletionThatOvertookItsReservationTakesTheRetryPath() throws Exception {
		when(handler.handle(event)).thenThrow(new ReservationNotFoundException(event.requestId()));
		brokerAnswers(RequestEventsListenerTest::confirmed);

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(ReservationNotFoundException.class);

		verify(rabbitTemplate).send(eq(""), eq(RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE),
				any(Message.class), any(CorrelationData.class));
		verify(channel).basicAck(TAG, false);
	}

	@Test
	void eachRetryStampsOneMoreAttempt() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		brokerAnswers(RequestEventsListenerTest::confirmed);

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(2L), channel, TAG))
				.isInstanceOf(IllegalStateException.class);

		ArgumentCaptor<Message> copy = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(any(), any(), copy.capture(), any(CorrelationData.class));
		assertThat((Object) copy.getValue().getMessageProperties()
				.getHeader(RequestEventsListener.RETRY_ATTEMPTS_HEADER)).isEqualTo(3L);
	}

	@Test
	void theLastAttemptIsDeadLetteredNotRetried() throws Exception {
		when(handler.handle(event)).thenThrow(new ReservationNotFoundException(event.requestId()));

		// four attempts already stamped: the delivery in hand is the fifth
		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(4L), channel, TAG))
				.isInstanceOf(ReservationNotFoundException.class);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void aNackedRetryCopyRequeuesTheOriginal() throws Exception {
		assertOriginalRequeuedWhen(correlation ->
				correlation.getFuture().complete(new CorrelationData.Confirm(false, "Channel closed")));
	}

	@Test
	void aReturnedRetryCopyRequeuesTheOriginal() throws Exception {
		assertOriginalRequeuedWhen(correlation -> {
			correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
					"", RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE));
			confirmed(correlation);
		});
	}

	@Test
	void anUnconfirmedRetryCopyRequeuesTheOriginal() throws Exception {
		// waits out the confirm ceiling once
		assertOriginalRequeuedWhen(correlation -> {
		});
	}

	@Test
	void aRetryCopyThatCannotBeSentRequeuesTheOriginal() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
				.when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(AmqpException.class);

		verify(channel).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	// requeued at once, never acknowledged (lost) or left unacknowledged (stuck)
	private void assertOriginalRequeuedWhen(Consumer<CorrelationData> brokerAnswer) throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		brokerAnswers(brokerAnswer);

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(AmqpException.class);

		verify(channel).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
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

	private static Message delivery(Long attemptsStamped) {
		MessageProperties properties = new MessageProperties();
		if (attemptsStamped != null) {
			properties.setHeader(RequestEventsListener.RETRY_ATTEMPTS_HEADER, attemptsStamped);
		}
		return new Message("{}".getBytes(), properties);
	}

	private static RequestCompleted completed() {
		return new RequestCompleted("e-1", Instant.parse("2026-10-14T08:00:00Z"), "order-service", "c-1",
				List.of("u-req", "u-cou"), "r-1", "u-req", "u-cou", "Techno Edge", "COM3-01-19", null);
	}
}
