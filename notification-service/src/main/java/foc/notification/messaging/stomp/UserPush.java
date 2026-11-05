/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — the envelope one replica
 * publishes and every replica delivers to its own simple broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

import foc.notification.service.NotificationDto;

/**
 * One push for one user's destination: what
 * {@code SimpMessagingTemplate.convertAndSendToUser} takes, carried
 * between replicas (DP10). {@code payload} is the frame the client
 * receives, unchanged ({@link NotificationDto}).
 */
public record UserPush(String userId, String destination, NotificationDto payload) {
}
