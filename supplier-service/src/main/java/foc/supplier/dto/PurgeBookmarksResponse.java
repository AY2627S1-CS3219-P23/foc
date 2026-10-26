/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.5, D12), POST /purge/bookmarks body: how many
 * bookmark rows were removed.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

public record PurgeBookmarksResponse(int deleted) {
}
