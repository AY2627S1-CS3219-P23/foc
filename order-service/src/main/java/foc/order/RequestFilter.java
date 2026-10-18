/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: the list endpoints' optional filters and sort (the author's
 * choice of filters: supplier, reward range, deadline and created
 * ranges, a sort parameter, on all three lists). Bound from the query
 * string; ranges follow credit-service's history (lower bound inclusive,
 * upper exclusive, amounts at least 1). Checked by RequestsQueryService.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * Query parameters shared by GET /requests, /requests/mine and
 * /users/{id}/requests; every one is optional.
 *
 * @param supplierId     only requests from this supplier
 * @param minCredit      reward at least this (inclusive)
 * @param maxCredit      reward at most this (inclusive)
 * @param deadlineAfter  deadline at or after this instant
 * @param deadlineBefore deadline before this instant
 * @param createdFrom    created at or after this instant
 * @param createdTo      created before this instant
 * @param sort           {@code createdAt} (newest first, the default) or
 *                       {@code deadline} (nearest first)
 */
public record RequestFilter(
        Long supplierId,
        Integer minCredit,
        Integer maxCredit,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime deadlineAfter,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime deadlineBefore,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
        String sort) {

    /** No filter, default order. */
    public static final RequestFilter NONE = new RequestFilter(null, null, null, null, null, null, null, null);
}
