/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the unread-count body of docs/notification-service.md "REST
 * API contract" — {"count": n} (author decision via options Q&A, D24).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.dto;

/** What the bell badge shows: how many of the caller's notifications are unread. */
public record UnreadCountResponse(long count) {
}
