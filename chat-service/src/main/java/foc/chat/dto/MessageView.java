/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: a message without its image bytes: history pages and live frames carry
 * hasImage, and the image is fetched by its own URL (the tool's choice,
 * listed in docs/chat-service.md).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageView(UUID id, UUID chatId, long senderId, String text, boolean hasImage, Instant sentAt) {

    // the repository's projection: an image always has its content type
    // (V1's check), so a non-null type means the message has an image
    public MessageView(UUID id, UUID chatId, long senderId, String text, String imageContentType,
            Instant sentAt) {
        this(id, chatId, senderId, text, imageContentType != null, sentAt);
    }
}
