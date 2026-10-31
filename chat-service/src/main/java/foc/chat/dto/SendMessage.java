/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the body of a STOMP SEND to /app/chats/{chatId}/messages: text, an
 * image, or both (C7), the image base64-encoded in the frame (C8).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

public record SendMessage(String text, Image image) {

    public record Image(String contentType, String data) {
    }
}
