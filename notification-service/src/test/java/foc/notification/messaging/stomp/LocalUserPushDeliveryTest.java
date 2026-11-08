/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: the local half of the fanout sends to the recipient's user
 * destination and swallows a failing broker send. Author review: Leong Wei
 * Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import foc.notification.service.NotificationDto;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LocalUserPushDeliveryTest {

	private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
	private final LocalUserPushDelivery delivery = new LocalUserPushDelivery(template);

	private static final NotificationDto DTO = new NotificationDto(7L, "request.accepted",
			Map.of("requestId", "req-1"), Instant.parse("2026-09-19T08:30:00Z"), false,
			Instant.parse("2026-09-19T08:30:01Z"));

	@Test
	void sendsToTheRecipientsUserQueue() {
		delivery.deliver(new UserPush("usr-req-1001", DTO));

		verify(template).convertAndSendToUser("usr-req-1001", LocalUserPushDelivery.NOTIFICATIONS_DESTINATION, DTO);
	}

	@Test
	void aFailingSendIsLoggedNotThrown() {
		doThrow(new IllegalStateException("broker")).when(template)
				.convertAndSendToUser(anyString(), anyString(), any(Object.class));

		assertThatCode(() -> delivery.deliver(new UserPush("usr-req-1001", DTO))).doesNotThrowAnyException();
	}
}
