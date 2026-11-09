/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: the cross-replica push envelope (team decision: AMQP fanout,
 * Leong Wei Zhi via options Q&A). Author review: Leong Wei Zhi, pending
 * pull request review.
 */
package foc.notification.messaging.stomp;

import foc.notification.service.NotificationDto;

/**
 * One push on its way to whichever replica holds the recipient's STOMP
 * session (D35): the recipient (the principal name a session
 * authenticated as) and the frame to deliver. Serialized as JSON on the
 * {@code notification.push} fanout exchange; every replica decodes it
 * and offers it to its own simple broker.
 */
public record UserPush(String recipientId, NotificationDto notification) {
}
