/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: post-commit push for issue #67 (design doc: processor emits
 * stored notifications; gateway pushes to each party's /user
 * destination within the 5 s budget, F1.2, Order NFR1.1-1.2).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

import foc.notification.entity.Notification;
import foc.notification.service.NotificationDto;
import foc.notification.service.NotificationStoredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Pushes each stored notification to its recipient's user destination
 * right after the processor's transaction commits — synchronously on
 * the consuming thread, well inside the 5 s budget (Order
 * NFR1.1-1.2). Duplicate, stale, and rolled-back events never publish
 * a {@link NotificationStoredEvent}, so they never push.
 *
 * <p>Push is best-effort: a recipient with no STOMP session simply
 * misses the frame (the REST list, issue #66, is the catch-up path).
 */
@Component
class NotificationPushListener {

	static final String NOTIFICATIONS_DESTINATION = "/queue/notifications";

	private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

	private final SimpMessagingTemplate messagingTemplate;
	private final ObjectMapper objectMapper;

	NotificationPushListener(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
		this.messagingTemplate = messagingTemplate;
		this.objectMapper = objectMapper;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onNotificationStored(NotificationStoredEvent event) {
		Notification notification = event.notification();
		try {
			messagingTemplate.convertAndSendToUser(notification.getRecipientId(),
					NOTIFICATIONS_DESTINATION, NotificationDto.from(notification, objectMapper));
		} catch (RuntimeException ex) {
			// Must not propagate: an AFTER_COMMIT exception would reach the
			// AMQP listener thread and nack an already-committed delivery,
			// whose redelivery dedupes to a no-op — stored but never pushed.
			log.warn("push failed for notification {}", notification.getId(), ex);
		}
	}
}
