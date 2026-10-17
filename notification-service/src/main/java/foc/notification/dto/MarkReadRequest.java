/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
 * Scope: the PATCH body of docs/notification-service.md "REST API
 * contract" — {"read": true|false} (author decision via options Q&A,
 * D24). Boxed Boolean so an absent field is null, which the
 * controller answers 400 rather than defaulting silently.
 * 2026-10-17, PR #198 Codex review: strict deserializer attached —
 * Jackson's default scalar coercion bound {"read":"true"} and
 * {"read":1}, answering 204 where the contract promises 400.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

/** The PATCH body: the read state the caller wants the notification in. */
public record MarkReadRequest(
		@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean read) {
}
