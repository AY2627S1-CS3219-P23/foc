/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: what a receiver's own queue (/user/queue/chat) gets for each
 * message sent to them: the chat's new unread count for the Chat button
 * badge (C19), and whether this message made the chat unread, with the
 * sender and a text preview for the toast and browser notification
 * (C16-C18, C20).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import java.util.UUID;

/**
 * {@code first} is true only for the message that took the receiver's
 * unread count from 0 to 1: the web app notifies on that one alone (C17).
 * {@code preview} is the text's first 80 characters, or null for an
 * image-only message ({@code hasImage} says so).
 */
public record UnreadFrame(UUID chatId, long requestId, long unreadCount, boolean first, long senderId,
        String preview, boolean hasImage) {
}
