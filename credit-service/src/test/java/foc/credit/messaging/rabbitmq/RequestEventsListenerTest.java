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
 * 2026-10-16, PR #190 review (Leong Wei Zhi), team decisions: a
 * returned copy dead-letters the original; a nacked, unconfirmed or
 * unsendable one requeues it only after the pause (mocked here), and
 * the event's own failure is kept as a suppressed exception. The
 * confirm timeout is passed in, so no test waits out 5 s.
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
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
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
	/** Short, so the unconfirmed-copy test does not wait out the deployed 5 s. */
	private static final long CONFIRM_TIMEOUT_MS = 50;

	private final RequestEventHandler handler = mock(RequestEventHandler.class);
	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final Channel channel = mock(Channel.class);
	private final RequestEventsListener.Pause pause = mock(RequestEventsListener.Pause.class);
	private final RequestEventsListener listener =
			new RequestEventsListener(handler, rabbitTemplate, MAX_ATTEMPTS, CONFIRM_TIMEOUT_MS, pause);
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
	void aReturnedRetryCopyDeadLettersTheOriginal() throws Exception {
		IllegalStateException failure = new IllegalStateException("database down");
		when(handler.handle(event)).thenThrow(failure);
		brokerAnswers(correlation -> {
			correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
					"", RabbitMqTopology.REQUEST_EVENTS_RETRY_QUEUE));
			confirmed(correlation);
		});

		// the retry queue is missing: no retry can succeed, so no requeue loop
		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(RequestEventsListener.RetryCopyReturnedException.class)
				.satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(failure));

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(pause);
	}

	@Test
	void aNackedRetryCopyRequeuesTheOriginalAfterThePause() throws Exception {
		assertOriginalRequeuedAfterThePauseWhen(correlation ->
				correlation.getFuture().complete(new CorrelationData.Confirm(false, "Channel closed")));
	}

	@Test
	void anUnconfirmedRetryCopyRequeuesTheOriginalAfterThePause() throws Exception {
		// waits out the confirm timeout once (CONFIRM_TIMEOUT_MS, kept short)
		assertOriginalRequeuedAfterThePauseWhen(correlation -> {
		});
	}

	@Test
	void aCancelledConfirmRequeuesTheOriginalAfterThePause() throws Exception {
		// CancellationException is not an AmqpException; it must still settle the delivery
		assertOriginalRequeuedAfterThePauseWhen(correlation -> correlation.getFuture().cancel(true));
	}

	@Test
	void aRetryCopyThatCannotBeSentRequeuesTheOriginalAfterThePause() throws Exception {
		IllegalStateException failure = new IllegalStateException("database down");
		when(handler.handle(event)).thenThrow(failure);
		doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
				.when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(AmqpException.class)
				.satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(failure));

		InOrder order = inOrder(pause, channel);
		order.verify(pause).sleep(CONFIRM_TIMEOUT_MS);
		order.verify(channel).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void anInterruptedPauseStillRequeuesTheOriginal() throws Exception {
		doThrow(new InterruptedException()).when(pause).sleep(anyLong());
		try {
			assertOriginalRequeuedAfterThePauseWhen(correlation ->
					correlation.getFuture().complete(new CorrelationData.Confirm(false, "Channel closed")));
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	// requeued only after the pause, never acknowledged (lost) or left unacknowledged (stuck)
	private void assertOriginalRequeuedAfterThePauseWhen(Consumer<CorrelationData> brokerAnswer)
			throws Exception {
		IllegalStateException failure = new IllegalStateException("database down");
		when(handler.handle(event)).thenThrow(failure);
		brokerAnswers(brokerAnswer);

		assertThatThrownBy(() -> listener.onRequestEvent(event, delivery(null), channel, TAG))
				.isNotInstanceOf(RequestEventsListener.RetryCopyReturnedException.class)
				.satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(failure));

		InOrder order = inOrder(pause, channel);
		order.verify(pause).sleep(CONFIRM_TIMEOUT_MS);
		order.verify(channel).basicNack(TAG, false, true);
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
