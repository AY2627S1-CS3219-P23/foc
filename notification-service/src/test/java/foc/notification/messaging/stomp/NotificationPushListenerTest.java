/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — unit tests that the
 * after-commit push is published to the fanout, and that a failed
 * publish never escapes the listener.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import foc.notification.entity.Notification;
import foc.notification.service.NotificationDto;
import foc.notification.service.NotificationStoredEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class NotificationPushListenerTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-11-05T08:00:00Z");

	private final UserPushFanout fanout = mock(UserPushFanout.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	// The listener's only collaborators: there is no SimpMessagingTemplate
	// to push locally with, so the fanout is the one way out.
	private final NotificationPushListener listener = new NotificationPushListener(fanout, jsonMapper);

	private static Notification notification() {
		return new Notification("usr-fan-1", "e-fan-1", "request.accepted",
				"{\"requestId\":\"ord-fan-1\"}", OCCURRED_AT);
	}

	@Test
	void publishesTheRecipientsPushToTheFanout() {
		Notification notification = notification();

		listener.onNotificationStored(new NotificationStoredEvent(notification));

		ArgumentCaptor<UserPush> push = ArgumentCaptor.forClass(UserPush.class);
		verify(fanout).publish(push.capture());
		assertThat(push.getValue().userId()).isEqualTo("usr-fan-1");
		assertThat(push.getValue().destination()).isEqualTo("/queue/notifications");
		assertThat(push.getValue().payload()).isEqualTo(NotificationDto.from(notification, jsonMapper));
		assertThat(push.getValue().payload().payload()).isEqualTo(Map.of("requestId", "ord-fan-1"));
	}

	@Test
	void aFailedPublishIsLoggedNotThrown() {
		doThrow(new IllegalStateException("broker down")).when(fanout).publish(any());

		assertThatCode(() -> listener.onNotificationStored(new NotificationStoredEvent(notification())))
				.doesNotThrowAnyException();
	}
}
