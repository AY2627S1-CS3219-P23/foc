/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — unit test that a received
 * push reaches this replica's broker at the push's user and destination.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import foc.notification.service.NotificationDto;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LocalUserPushDeliveryTest {

	@Test
	void sendsThePayloadToTheUsersDestination() {
		SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
		NotificationDto payload = new NotificationDto(7L, "request.accepted", Map.of("requestId", "ord-1"),
				Instant.parse("2026-11-05T08:00:00Z"), false, Instant.parse("2026-11-05T08:00:01Z"));

		new LocalUserPushDelivery(template).deliver(new UserPush("usr-1", "/queue/notifications", payload));

		verify(template).convertAndSendToUser("usr-1", "/queue/notifications", payload);
		verifyNoMoreInteractions(template);
	}
}
