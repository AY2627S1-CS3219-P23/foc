/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: the AFTER_COMMIT listener publishes the stored notification as a
 * push envelope to the fanout port and never lets a failing publish out
 * (it would nack a committed delivery). Author review: Leong Wei Zhi,
 * pending pull request review.
 */
package foc.notification.messaging.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import foc.notification.entity.Notification;
import foc.notification.service.NotificationStoredEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class NotificationPushListenerTest {

	private final UserPushFanout fanout = mock(UserPushFanout.class);
	private final NotificationPushListener listener =
			new NotificationPushListener(fanout, JsonMapper.builder().build());

	private static Notification stored() {
		return new Notification("usr-req-1001", "e-1", "request.accepted",
				"{\"requestId\":\"req-1\"}", Instant.parse("2026-09-19T08:30:00Z"));
	}

	@Test
	void publishesTheStoredNotificationToItsRecipient() {
		listener.onNotificationStored(new NotificationStoredEvent(stored()));

		ArgumentCaptor<UserPush> push = ArgumentCaptor.forClass(UserPush.class);
		verify(fanout).publish(push.capture());
		assertThat(push.getValue().recipientId()).isEqualTo("usr-req-1001");
		assertThat(push.getValue().notification().eventType()).isEqualTo("request.accepted");
		assertThat(push.getValue().notification().payload()).isEqualTo(Map.of("requestId", "req-1"));
	}

	@Test
	void aFailingPublishIsLoggedNotThrown() {
		doThrow(new IllegalStateException("broker down")).when(fanout).publish(any());

		assertThatCode(() -> listener.onNotificationStored(new NotificationStoredEvent(stored())))
				.doesNotThrowAnyException();
	}
}
