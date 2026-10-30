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
// 2026-10-19, Claude Code (Fable 5), issue #221: read-time hydration per
// the author's D32 decision (options Q&A, supersedes D30/D31) — payloads
// carry courierId only; the caller passes a resolved-name map and the
// stored requesterName/courierName keys of pre-D32 rows are ignored.
// The actor-less copy doubles as the loading/lookup-failed rendering.
// 2026-10-27, Claude Code (Fable 5), issue #232: requestIdOf exported so
// rows can navigate to the referenced request; requestLabel refactored
// onto it (same copy).
// 2026-10-29, Claude Code (Opus 5.5), issue #241: request.disputed and
// request.dispute-resolved templates (cosmetic, DSP-9): the category, and
// how the reward was divided. Both go to requester and courier alike, so
// the copy names neither as "you".
// Author review: Ryan Ang, pending pull request review.

import { credits, disputeCategoryLabels } from '@/features/order/status'
import type { DisputeCategory } from '@/features/order/types'
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

function count(payload: Record<string, unknown>, key: string): number | null {
  const value = payload[key]
  return typeof value === 'number' && Number.isInteger(value) && value >= 0
    ? value
    : null
}

// a category this web build doesn't know (D18) shows no label
function categoryLabel(payload: Record<string, unknown>): string | null {
  const category = text(payload, 'category')
  return category !== null && Object.hasOwn(disputeCategoryLabels, category)
    ? disputeCategoryLabels[category as DisputeCategory]
    : null
}

// Every cataloged request event carries requestId (a numeric string in
// production — order-service stringifies the Long); null means the row
// references no request and cannot navigate.
export function requestIdOf(payload: Record<string, unknown>): string | null {
  const value = payload.requestId
  if (typeof value === 'string' && value !== '') return value
  if (typeof value === 'number') return String(value)
  return null
}

// "Request #1048" for numeric IDs, "Request req-…" otherwise — the
// wireframe's copy uses the # form
function requestLabel(payload: Record<string, unknown>): string {
  const id = requestIdOf(payload)
  if (id === null) return 'A request'
  return /^\d+$/.test(id) ? `Request #${id}` : `Request ${id}`
}

// The courier party field — the only id the templates render today
// (requesterId rides on every request event but no copy uses it).
export function courierIdOf(payload: Record<string, unknown>): string | null {
  return text(payload, 'courierId')
}

const templates: Record<
  string,
  (
    payload: Record<string, unknown>,
    courier: string | null,
  ) => NotificationMessage
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
  'request.accepted': (p, courier) => ({
    icon: '✅',
    title: courier
      ? `${requestLabel(p)} accepted by ${courier}`
      : `${requestLabel(p)} accepted`,
  }),
  'request.collected': (p, courier) => ({
    icon: '📦',
    title: courier
      ? `${requestLabel(p)} collected by ${courier}`
      : `${requestLabel(p)} collected`,
  }),
  'request.courier-arrived': (p) => ({
    icon: '📍',
    title: `Courier has arrived at drop-off for ${requestLabel(p)}`,
  }),
  'request.completed': (p) => ({
    icon: '✔️',
    title: `${requestLabel(p)} marked as completed`,
  }),
  'request.dropped': (p, courier) => ({
    icon: '↩️',
    title: courier
      ? `${requestLabel(p)} dropped by ${courier}`
      : `${requestLabel(p)} was dropped by its courier`,
  }),
  'request.cancelled': (p) => ({
    icon: '🚫',
    title: `${requestLabel(p)} cancelled`,
  }),
  'request.expired': (p) => ({
    icon: '⌛',
    title: `${requestLabel(p)} expired`,
  }),
  'request.disputed': (p) => {
    const category = categoryLabel(p)
    return {
      icon: '⚠️',
      title: category
        ? `Problem reported with ${requestLabel(p)}: ${category}`
        : `Problem reported with ${requestLabel(p)}`,
    }
  },
  'request.dispute-resolved': (p) => {
    const toCourier = count(p, 'courierAmount')
    const toRequester = count(p, 'requesterAmount')
    return {
      icon: '⚖️',
      title:
        toCourier !== null && toRequester !== null
          ? `Dispute on ${requestLabel(p)} resolved: ${credits(toCourier)} to the courier, ${credits(toRequester)} refunded`
          : `Dispute on ${requestLabel(p)} resolved`,
    }
  },
}

// names: courierId → username, resolved at read time by the caller
// (D32). A missing entry — still loading, lookup failed, user deleted —
// falls through to the actor-less copy, exactly like D30's absent key.
export function notificationMessage(
  notification: NotificationDto,
  names: ReadonlyMap<string, string> = new Map(),
): NotificationMessage {
  const template = templates[notification.eventType]
  if (template) {
    const courierId = courierIdOf(notification.payload)
    const courier = courierId === null ? null : (names.get(courierId) ?? null)
    return template(notification.payload, courier)
  }
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
