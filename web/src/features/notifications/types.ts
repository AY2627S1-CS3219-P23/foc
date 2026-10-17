// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
// Scope: the notification REST API's shapes (backend D24,
// docs/notification-service.md "REST API contract"). NotificationDto
// mirrors the backend record of the same name — one type for both the
// STOMP push frame and the REST list, as the backend intends; the page
// envelope matches supplier's PagedResponse field-for-field (each
// domain declares its own copy, like the backends do).
// Reviewed by: Leong Wei Zhi (via pull request).

export interface NotificationDto {
  id: number
  eventType: string
  // the event's business fields, parsed (never double-encoded JSON);
  // keys vary by eventType — eventMessage.ts knows the cataloged ones
  payload: Record<string, unknown>
  occurredAt: string
  read: boolean
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UnreadCountResponse {
  count: number
}
