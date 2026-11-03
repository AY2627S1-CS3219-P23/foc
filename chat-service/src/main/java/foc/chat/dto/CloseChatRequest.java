/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: order-service's close call (C11).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.dto;

import jakarta.validation.constraints.NotNull;

public record CloseChatRequest(@NotNull Long requestId) {
}
