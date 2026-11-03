/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the chat as the REST API and the CLOSED frame show it.
 * 2026-10-31: the caller's unread count (C19; 0 for admins).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import foc.chat.entity.Chat;
import foc.chat.entity.ChatStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatView(UUID id, long requestId, long requesterId, long courierId, ChatStatus status,
        Instant openedAt, Instant closedAt, long unreadCount) {

    public static ChatView of(Chat chat) {
        return of(chat, 0);
    }

    public static ChatView of(Chat chat, long unreadCount) {
        return new ChatView(chat.getId(), chat.getRequestId(), chat.getRequesterId(), chat.getCourierId(),
                chat.getStatus(), chat.getOpenedAt(), chat.getClosedAt(), unreadCount);
    }
}
