/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: the local half of the cross-replica push — the per-user STOMP
 * send that NotificationPushListener used to make directly (issue #67),
 * now invoked for every push the fanout delivers to this replica.
 * Author review: Leong Wei Zhi, pending pull request review.
 * Same day, PR #306 review (Sinnez1): a push without a recipient or a
 * notification is logged and dropped before the send, and the failure log
 * no longer reaches into the notification. Author review: Leong Wei Zhi,
 * pending pull request review.
 */
package foc.notification.messaging.stomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers a {@link UserPush} into this replica's simple broker. The
 * user destination resolves against this JVM's own session registry
 * only, so a replica without a session for the recipient sends nothing
 * — which is what makes the fanout exactly-once per browser (D35): a
 * socket lives on exactly one replica.
 *
 * <p>Best-effort, as before: a recipient with no STOMP session simply
 * misses the frame (the REST list, issue #66, is the catch-up path).
 */
@Component
public class LocalUserPushDelivery {

	static final String NOTIFICATIONS_DESTINATION = "/queue/notifications";

	private static final Logger log = LoggerFactory.getLogger(LocalUserPushDelivery.class);

	private final SimpMessagingTemplate messagingTemplate;

	LocalUserPushDelivery(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void deliver(UserPush push) {
		// a malformed frame (e.g. {}) decodes to nulls: drop it here rather
		// than let the send throw into the container (PR #306 review)
		if (push.recipientId() == null || push.notification() == null) {
			log.warn("dropped a push without a recipient or notification");
			return;
		}
		try {
			messagingTemplate.convertAndSendToUser(push.recipientId(), NOTIFICATIONS_DESTINATION,
					push.notification());
		} catch (RuntimeException ex) {
			log.warn("push failed for recipient {}", push.recipientId(), ex);
		}
	}
}
