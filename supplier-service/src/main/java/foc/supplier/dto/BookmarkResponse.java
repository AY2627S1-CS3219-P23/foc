/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8), PUT /suppliers/{id}/bookmark body: the supplier
 * is bookmarked, with the caller's new bookmark count.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import java.time.Instant;

public record BookmarkResponse(String supplierId, boolean bookmarked, Instant bookmarkedAt, int count) {
}
