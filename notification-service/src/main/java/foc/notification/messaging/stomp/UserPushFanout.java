/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05.
 * Scope: issue #48 (DP10): cross-replica STOMP fanout over AMQP (team
 * decision, Leong Wei Zhi via options Q&A) — the outbound port, so this
 * package stays broker-free (D11); the RabbitMQ adapter implements it.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

/**
 * Sends a push to every replica, this one included; each replica hands
 * it to {@link LocalUserPushDelivery}. Callers must not also deliver
 * locally — the publishing replica gets its own copy back.
 */
public interface UserPushFanout {

	void publish(UserPush push);
}
