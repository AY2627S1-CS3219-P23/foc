/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), GET /bookmarks body: the caller's own bookmarks,
 * newest first, with their count and the per-user cap so the web client
 * never hard-codes 5.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import java.time.Instant;
import java.util.List;

public record BookmarkListResponse(int count, int limit, List<Item> bookmarks) {

    public record Item(String supplierId, String name, Instant bookmarkedAt) {
    }
}
