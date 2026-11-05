/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): the envelope one replica publishes and every
 * replica delivers to its own simple broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import tools.jackson.databind.JsonNode;

/**
 * One push, as the replica that made it would have sent it: a TOPIC push
 * goes to {@code destination} ({@code /topic/chats/{chatId}}), a USER push
 * to {@code destination} of {@code user} ({@code /queue/chat}). The
 * payload is the frame ({@code ChatFrame} or {@code UnreadFrame}) as JSON,
 * written by the same mapper as the STOMP converter, so subscribers get
 * the frame unchanged.
 */
record ChatPush(Kind kind, String user, String destination, JsonNode payload) {

    enum Kind { TOPIC, USER }

    static ChatPush topic(String destination, JsonNode payload) {
        return new ChatPush(Kind.TOPIC, null, destination, payload);
    }

    static ChatPush user(String user, String destination, JsonNode payload) {
        return new ChatPush(Kind.USER, user, destination, payload);
    }
}
