/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: client-facing notification shape for issue #67 (STOMP push
 * frame); intended to be reused by issue #66's REST list API so the
 * frontend needs one type for both.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.service;

import foc.notification.entity.Notification;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * What the client sees for one notification — over the STOMP push
 * now (issue #67) and the REST list later (issue #66), so one
 * frontend type serves both. The stored payload JSON string goes out
 * parsed as an object (no double-encoded JSON for the client to
 * re-parse), using Boot's auto-configured mapper — the same mapper
 * that serialized it in the processor (D15), so it round-trips.
 * {@code recipientId} (the session owner) and {@code eventId} (a
 * dedupe internal) are deliberately omitted.
 */
public record NotificationDto(
		Long id,
		String entityType,
		String entityId,
		String eventType,
		Map<String, Object> payload,
		Instant occurredAt,
		boolean read,
		Instant createdAt) {

	@SuppressWarnings("unchecked")
	public static NotificationDto from(Notification notification, ObjectMapper objectMapper) {
		return new NotificationDto(
				notification.getId(),
				notification.getEntityType(),
				notification.getEntityId(),
				notification.getEventType(),
				objectMapper.readValue(notification.getPayload(), Map.class),
				notification.getOccurredAt(),
				notification.isRead(),
				notification.getCreatedAt());
	}
}
