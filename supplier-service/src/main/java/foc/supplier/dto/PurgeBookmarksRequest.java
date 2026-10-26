/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.5, D12), POST /purge/bookmarks body: the ids of
 * accounts user-service has purged. A body rather than a query string, so
 * a long list cannot run into URL length limits.
 * Reviewed by: [pending]
 */
package foc.supplier.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PurgeBookmarksRequest(
        @NotNull @Size(max = MAX_USER_IDS) List<@NotNull Long> userIds) {

    public static final int MAX_USER_IDS = 1000;
}
