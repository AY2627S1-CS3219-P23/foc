/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: post-commit push for issue #67 (design doc: processor emits
 * stored notifications; gateway pushes to each party's /user
 * destination within the 5 s budget, F1.2, Order NFR1.1-1.2).
 * Reviewed by: Leong Wei Zhi (via pull request).
 * 2026-11-05, Claude Code (Opus 5.5), issue #48 (DP10): cross-replica
 * STOMP fanout over AMQP (team decision, Leong Wei Zhi via options Q&A)
 * — the push is published to every replica through UserPushFanout
 * instead of sent to this replica's broker only. Author review: Leong
 * Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import foc.notification.entity.Notification;
import foc.notification.service.NotificationDto;
import foc.notification.service.NotificationStoredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>The push goes to every replica ({@link UserPushFanout}, DP10), since
 * the recipient's session may be on any of them; this replica delivers
 * its own copy when it comes back, so nothing is sent locally here.
 *
 * <p>Push is best-effort: a recipient with no STOMP session simply
 * misses the frame (the REST list, issue #66, is the catch-up path).
 */
@Component
class NotificationPushListener {

	static final String NOTIFICATIONS_DESTINATION = "/queue/notifications";

	private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

	private final UserPushFanout fanout;
	private final ObjectMapper objectMapper;

	NotificationPushListener(UserPushFanout fanout, ObjectMapper objectMapper) {
		this.fanout = fanout;
		this.objectMapper = objectMapper;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onNotificationStored(NotificationStoredEvent event) {
		Notification notification = event.notification();
		try {
			fanout.publish(new UserPush(notification.getRecipientId(), NOTIFICATIONS_DESTINATION,
					NotificationDto.from(notification, objectMapper)));
		} catch (RuntimeException ex) {
			// Must not propagate: an AFTER_COMMIT exception would reach the
			// AMQP listener thread and nack an already-committed delivery,
			// whose redelivery dedupes to a no-op — stored but never pushed.
			log.warn("push failed for notification {}", notification.getId(), ex);
		}
	}
}
