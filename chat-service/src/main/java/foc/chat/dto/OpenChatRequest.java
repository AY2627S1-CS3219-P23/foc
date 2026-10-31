/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: order-service's open call (C11).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import jakarta.validation.constraints.NotNull;

public record OpenChatRequest(@NotNull Long requestId, @NotNull Long requesterId, @NotNull Long courierId) {
}
