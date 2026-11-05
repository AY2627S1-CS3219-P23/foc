/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — the receiving end: a push
 * from any replica goes to this replica's simple broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers a fanned-out push to the sessions connected to this replica.
 * A user with no session here gets nothing from this replica; the
 * replica holding their session delivers it.
 */
@Component
public class LocalUserPushDelivery {

	private final SimpMessagingTemplate messagingTemplate;

	LocalUserPushDelivery(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void deliver(UserPush push) {
		messagingTemplate.convertAndSendToUser(push.userId(), push.destination(), push.payload());
	}
}
