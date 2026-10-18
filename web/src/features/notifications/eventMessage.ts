// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
// Scope: one display template per cataloged request event
// (docs/notification-service.md's event catalog), using the payload's
// requesterName/courierName display fields with actor-less copy when
// a name is absent. Originally written against backend D29 (names in
// the events); D30 superseded it the same day — notification-service
// now enriches the payload at ingest — with the payload shape and this
// file unchanged: a name can still be missing (lookup failed, user
// deleted, enrichment disabled), so the copy must degrade, never
// break. Unknown event types render a safe fallback: new types can
// appear without web changes (D18). Decisions the author's via options
// Q&A.
// 2026-10-18: request.dropped template (courier withdrew; same
// degrade-without-name pattern as request.accepted); pending pull
// request review.
// Reviewed by: Leong Wei Zhi (via pull request).

import type { NotificationDto } from './types'

export interface NotificationMessage {
  icon: string
  title: string
}

// payload fields are untyped on the wire; read them defensively
function text(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key]
  return typeof value === 'string' && value !== '' ? value : null
}

// "Request #1048" for numeric IDs, "Request req-…" otherwise — the
// wireframe's copy uses the # form
function requestLabel(payload: Record<string, unknown>): string {
  const id = text(payload, 'requestId') ?? String(payload.requestId ?? '')
  if (id === '') return 'A request'
  return /^\d+$/.test(id) ? `Request #${id}` : `Request ${id}`
}

const templates: Record<
  string,
  (payload: Record<string, unknown>) => NotificationMessage
> = {
  'request.created': (p) => ({
    icon: '📝',
    title: `${requestLabel(p)} created`,
  }),
  'request.submitted': (p) => ({
    icon: '📨',
    title: `${requestLabel(p)} submitted`,
  }),
  'request.rejected': (p) => {
    const reason = text(p, 'reason')
    return {
      icon: '🚫',
      title: reason
        ? `${requestLabel(p)} rejected: ${reason}`
        : `${requestLabel(p)} rejected`,
    }
  },
  'request.accepted': (p) => {
    const courier = text(p, 'courierName')
    return {
      icon: '✅',
      title: courier
        ? `${requestLabel(p)} accepted by ${courier}`
        : `${requestLabel(p)} accepted`,
    }
  },
  'request.collected': (p) => {
    const courier = text(p, 'courierName')
    return {
      icon: '📦',
      title: courier
        ? `${requestLabel(p)} collected by ${courier}`
        : `${requestLabel(p)} collected`,
    }
  },
  'request.courier-arrived': (p) => ({
    icon: '📍',
    title: `Courier has arrived at drop-off for ${requestLabel(p)}`,
  }),
  'request.completed': (p) => ({
    icon: '✔️',
    title: `${requestLabel(p)} marked as completed`,
  }),
  'request.dropped': (p) => {
    const courier = text(p, 'courierName')
    return {
      icon: '↩️',
      title: courier
        ? `${requestLabel(p)} dropped by ${courier}`
        : `${requestLabel(p)} was dropped by its courier`,
    }
  },
  'request.cancelled': (p) => ({
    icon: '🚫',
    title: `${requestLabel(p)} cancelled`,
  }),
  'request.expired': (p) => ({
    icon: '⌛',
    title: `${requestLabel(p)} expired`,
  }),
}

export function notificationMessage(
  notification: NotificationDto,
): NotificationMessage {
  const template = templates[notification.eventType]
  if (template) return template(notification.payload)
  // an unknown type still renders (D18: new types need no web release) —
  // name the type rather than invent a claim about it
  return { icon: '🔔', title: `Update: ${notification.eventType}` }
}

// "10 min ago" / "1h ago" / "2d ago" per the wireframe; dates are close
// (the retention window is 30 days), so days is the coarsest unit
export function relativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const seconds = Math.max(0, Math.floor((now.getTime() - then) / 1000))
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}
