// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
// Scope: the notification REST calls, routed through the shared
// apiFetch wrapper (src/lib/api/http.ts) so auth headers, the
// token-bearing-401 sign-out and RFC 9457 error parsing stay in one
// place. Paths per the backend's D24 (docs/notification-service.md
// "REST API contract"); all routes are the caller's own rows — no user
// ID ever appears in a path.
// Reviewed by: Leong Wei Zhi (via pull request).

import { apiFetch } from '@/lib/api/http'
import type {
  NotificationDto,
  PageResponse,
  UnreadCountResponse,
} from './types'

export interface NotificationListParams {
  page?: number
  size?: number
  unread?: boolean
}

function toQuery(params: NotificationListParams): string {
  const search = new URLSearchParams()
  if (params.page !== undefined) search.set('page', String(params.page))
  if (params.size !== undefined) search.set('size', String(params.size))
  if (params.unread) search.set('unread', 'true')
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export function listNotifications(params: NotificationListParams = {}) {
  return apiFetch<PageResponse<NotificationDto>>(
    'notification',
    `/notifications/me${toQuery(params)}`,
  )
}

export function getUnreadCount() {
  return apiFetch<UnreadCountResponse>(
    'notification',
    '/notifications/me/unread-count',
  )
}

// 204 either way; marking an already-read row read again is the same
// success (backend D26 — the write is idempotent)
export function setNotificationRead(id: number, read: boolean) {
  return apiFetch<void>('notification', `/notifications/me/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ read }),
  })
}

export function markAllRead() {
  return apiFetch<void>('notification', '/notifications/me/read-all', {
    method: 'POST',
  })
}
