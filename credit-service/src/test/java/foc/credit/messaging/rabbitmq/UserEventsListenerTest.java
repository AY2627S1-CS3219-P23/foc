/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: unit tests for the user event listener, after
 * RequestEventsListenerTest: every handler result acknowledged (USR-6),
 * a failure republished to the user-events retry queue, the last
 * attempt dead-lettered. Same day, PR review: NO_USER dead-lettered at
 * once, and a returned or unconfirmed retry copy, each on this
 * listener's own queue and header.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.ReturnedMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.rabbitmq.client.Channel;

import foc.contracts.events.user.UserPurged;
import foc.credit.service.AccountResult;
import foc.credit.service.UserEventHandler;

class UserEventsListenerTest {

	private static final int MAX_ATTEMPTS = 5;
	private static final long TAG = 7L;
	private static final long CONFIRM_TIMEOUT_MS = 50;

	private final UserEventHandler handler = mock(UserEventHandler.class);
	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final Channel channel = mock(Channel.class);
	private final UserEventsListener.Pause pause = mock(UserEventsListener.Pause.class);
	private final UserEventsListener listener = new UserEventsListener(handler, rabbitTemplate, MAX_ATTEMPTS,
			CONFIRM_TIMEOUT_MS, pause);
	private final UserPurged event = new UserPurged("e-1", Instant.parse("2026-10-28T03:00:00Z"), "user-service",
			"e-1", List.of(), "42");

	@ParameterizedTest
	@EnumSource(value = AccountResult.class, names = "NO_USER", mode = EnumSource.Mode.EXCLUDE)
	void everyResultButNoUserIsAcknowledged(AccountResult result) throws Exception {
		when(handler.handle(event)).thenReturn(result);

		listener.onUserEvent(event, delivery(null), channel, TAG);

		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void anEventNamingNoUserIsDeadLetteredWithoutARetry() throws Exception {
		when(handler.handle(event)).thenReturn(AccountResult.NO_USER);

		listener.onUserEvent(event, delivery(null), channel, TAG);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void aReturnedRetryCopyDeadLettersTheOriginal() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		doAnswer(invocation -> {
			CorrelationData correlation = invocation.getArgument(3);
			correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE", "",
					RabbitMqTopology.USER_EVENTS_RETRY_QUEUE));
			correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
			return null;
		}).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

		assertThatThrownBy(() -> listener.onUserEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(UserEventsListener.RetryCopyReturnedException.class);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void anUnconfirmedRetryCopyRequeuesTheOriginalAfterThePause() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		// the broker never answers: the confirm times out

		assertThatThrownBy(() -> listener.onUserEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(org.springframework.amqp.AmqpException.class);

		verify(pause).sleep(CONFIRM_TIMEOUT_MS);
		verify(channel).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void aFailureIsRepublishedToTheUserEventsRetryQueue() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));
		doAnswer(invocation -> {
			CorrelationData correlation = invocation.getArgument(3);
			correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
			return null;
		}).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

		assertThatThrownBy(() -> listener.onUserEvent(event, delivery(null), channel, TAG))
				.isInstanceOf(IllegalStateException.class);

		ArgumentCaptor<Message> copy = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(eq(""), eq(RabbitMqTopology.USER_EVENTS_RETRY_QUEUE), copy.capture(),
				any(CorrelationData.class));
		assertThat((Object) copy.getValue().getMessageProperties()
				.getHeader(UserEventsListener.RETRY_ATTEMPTS_HEADER)).isEqualTo(1L);
		verify(channel).basicAck(TAG, false);
	}

	@Test
	void theLastAttemptIsDeadLettered() throws Exception {
		when(handler.handle(event)).thenThrow(new IllegalStateException("database down"));

		assertThatThrownBy(() -> listener.onUserEvent(event, delivery(4L), channel, TAG))
				.isInstanceOf(IllegalStateException.class);

		verify(channel).basicNack(TAG, false, false);
		verifyNoInteractions(rabbitTemplate);
	}

	private static Message delivery(Long attemptsStamped) {
		MessageProperties properties = new MessageProperties();
		if (attemptsStamped != null) {
			properties.setHeader(UserEventsListener.RETRY_ATTEMPTS_HEADER, attemptsStamped);
		}
		return new Message("{}".getBytes(), properties);
	}
}
