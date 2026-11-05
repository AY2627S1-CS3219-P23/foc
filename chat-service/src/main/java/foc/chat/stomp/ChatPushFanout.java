/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): the outbound side as an interface, so the
 * broker-free STOMP test can loop pushes back in-process.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

/**
 * Sends a push to every replica, this one included; each hands it to
 * {@link LocalChatPushDelivery}. Callers must not also deliver locally:
 * the publishing replica gets its own copy back.
 */
interface ChatPushFanout {

    void publish(ChatPush push);
}
