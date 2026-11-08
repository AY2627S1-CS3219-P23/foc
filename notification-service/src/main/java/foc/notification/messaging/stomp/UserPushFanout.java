/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (D35).
 * Scope: outbound port for the cross-replica push, so this package stays
 * free of AMQP types (D11, ArchitectureTest). Author review: Leong Wei
 * Zhi, pending pull request review.
 */
package foc.notification.messaging.stomp;

/**
 * Hands a push to every replica of this service (D35). The adapter
 * lives in {@code messaging.rabbitmq}; the publisher's own copy comes
 * back through the same path, so callers never deliver locally as well.
 */
public interface UserPushFanout {

	void publish(UserPush push);
}
