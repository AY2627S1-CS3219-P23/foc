/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the PATCH body of docs/notification-service.md "REST API
 * contract" — {"read": true|false} (author decision via options Q&A,
 * D24). Boxed Boolean so an absent field is null, which the
 * controller answers 400 rather than defaulting silently.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.dto;

/** The PATCH body: the read state the caller wants the notification in. */
public record MarkReadRequest(Boolean read) {
}
