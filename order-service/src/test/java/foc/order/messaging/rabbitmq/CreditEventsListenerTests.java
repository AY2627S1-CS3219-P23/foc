/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-15.
 * Scope: unit tests for CreditEventsListener with mocks (no broker): it
 * acks a handled reply, dead-letters one naming no known request,
 * republishes a failed reply to the retry queue while attempts remain
 * and dead-letters it once they run out, and never acks a reply whose
 * retry copy the broker did not confirm.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import foc.contracts.events.credit.CreditReserved;
import foc.contracts.events.request.RequestSubmitted;
import foc.order.CreditReplyHandler;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreditEventsListenerTests {

	private static final long TAG = 42L;
	private static final int MAX_ATTEMPTS = 5;

	private final CreditReplyHandler handler = mock(CreditReplyHandler.class);
	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final Channel channel = mock(Channel.class);
	private final CreditEventsListener listener = new CreditEventsListener(handler, rabbitTemplate, MAX_ATTEMPTS);

	private final CreditReserved reply = new CreditReserved("e1", Instant.now(), "credit-service", "5",
			List.of(), "5", "1", 3);

	@Test
	void handledReplyIsAcked() throws Exception {
		when(handler.handle(reply)).thenReturn(CreditReplyHandler.Outcome.HANDLED);

		listener.onCreditEvent(reply, message(null), channel, TAG);

		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void replyForUnknownRequestIsDeadLettered() throws Exception {
		when(handler.handle(reply)).thenReturn(CreditReplyHandler.Outcome.UNKNOWN_REQUEST);

		listener.onCreditEvent(reply, message(null), channel, TAG);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void failedReplyIsRetriedWhileAttemptsRemain() throws Exception {
		when(handler.handle(reply)).thenThrow(new IllegalStateException("version moved on"));
		confirmRepublish(true);
		Message delivered = message(null);

		assertThrows(IllegalStateException.class, () -> listener.onCreditEvent(reply, delivered, channel, TAG));

		verify(rabbitTemplate).send(eq(""), eq(RabbitMqTopology.CREDIT_EVENTS_RETRY_QUEUE), same(delivered),
				any(CorrelationData.class));
		assertEquals(Long.valueOf(1),
				delivered.getMessageProperties().<Long>getHeader(CreditEventsListener.RETRY_ATTEMPTS_HEADER));
		verify(channel).basicAck(TAG, false);
	}

	@Test
	void failedReplyIsDeadLetteredOnceAttemptsRunOut() throws Exception {
		when(handler.handle(reply)).thenThrow(new IllegalStateException("still failing"));

		assertThrows(IllegalStateException.class,
				() -> listener.onCreditEvent(reply, message((long) MAX_ATTEMPTS - 1), channel, TAG));

		verify(channel).basicNack(TAG, false, false);
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void unconfirmedRetryLeavesTheOriginalUnsettled() throws Exception {
		when(handler.handle(reply)).thenThrow(new IllegalStateException("failing"));
		confirmRepublish(false);

		assertThrows(AmqpException.class, () -> listener.onCreditEvent(reply, message(null), channel, TAG));

		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void nonCreditEventIsAckedWithoutAction() throws Exception {
		RequestSubmitted stray = new RequestSubmitted("e2", Instant.now(), "order-service", "5", List.of("1"),
				"5", "1", "The Deck", "COM3 Level 2", null, 3);

		listener.onCreditEvent(stray, message(null), channel, TAG);

		verify(channel).basicAck(TAG, false);
		verifyNoInteractions(handler);
	}

	// The broker's answer to the retry republish, delivered on the correlation.
	private void confirmRepublish(boolean ack) {
		doAnswer(invocation -> {
			CorrelationData correlation = invocation.getArgument(3);
			correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nacked"));
			return null;
		}).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
	}

	private static Message message(Long priorAttempts) {
		MessageProperties properties = new MessageProperties();
		if (priorAttempts != null) {
			properties.setHeader(CreditEventsListener.RETRY_ATTEMPTS_HEADER, priorAttempts);
		}
		return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
	}
}
