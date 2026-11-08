/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5.1), 2026-11-08, issue #305 (team decision:
 * AMQP fanout for the cross-replica STOMP push, Leong Wei Zhi via options
 * Q&A). Scope: the push envelope every chat-service replica receives.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import tools.jackson.databind.JsonNode;

/**
 * One STOMP push on its way to every replica (issue #305): a topic
 * broadcast when {@code user} is null, otherwise a send to that user's
 * destination. The frame travels as its JSON tree so {@code ChatFrame}
 * and {@code UnreadFrame} share one envelope and reach the browser
 * byte-identical to a direct send.
 */
public record ChatPush(String user, String destination, JsonNode payload) {
}
