/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: what subscribers of /topic/chats/{chatId} receive: a new message, or
 * the chat closing (so the pop-up can stop accepting input).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import java.util.UUID;

public record ChatFrame(String type, UUID chatId, MessageView message) {

    public static ChatFrame message(MessageView message) {
        return new ChatFrame("MESSAGE", message.chatId(), message);
    }

    public static ChatFrame closed(UUID chatId) {
        return new ChatFrame("CLOSED", chatId, null);
    }
}
