// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: status labels/colours, the five-step progress tracker and the
// date/credit formatting shared by the order pages.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: order-service's states
// (PENDING and REJECTED added; OPEN / PICKED_UP / DELIVERED renamed
// CREATED / COLLECTED / ARRIVED; DISPUTED removed), and the date lines use
// the fields the backend has (no updatedAt).
// 2026-10-20: activeStatuses / finishedStatuses, the two pages' groups.
// Author review: [pending]
// 2026-10-27, Claude Code (Opus 5.5), issue #242: labels for the two
// dispute states and for each close reason (admin failed orders).
// Author review: Ryan Ang, pending pull request review.

import type {
  CloseReason,
  RequestDetail,
  RequestStatus,
  RequestSummary,
} from './types'

export const AUTO_COMPLETE_MS = 60 * 60 * 1000 // 1 hour after courier arrival

export const statusLabels: Record<RequestStatus, string> = {
  PENDING: 'Reserving credits',
  CREATED: 'Open',
  ACCEPTED: 'Accepted',
  COLLECTED: 'Collected',
  ARRIVED: 'Arrived',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  REJECTED: 'Rejected',
  DISPUTED: 'Disputed',
  RESOLVED: 'Resolved',
}

export const statusChipClasses: Record<RequestStatus, string> = {
  PENDING: 'bg-yellow-50 text-yellow-700',
  CREATED: 'bg-blue-50 text-blue-700',
  ACCEPTED: 'bg-gray-100 text-gray-700',
  COLLECTED: 'bg-amber-50 text-amber-700',
  ARRIVED: 'bg-purple-50 text-purple-700',
  COMPLETED: 'bg-green-50 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
  EXPIRED: 'bg-gray-100 text-gray-500',
  REJECTED: 'bg-red-50 text-red-700',
  DISPUTED: 'bg-orange-50 text-orange-700',
  RESOLVED: 'bg-gray-100 text-gray-500',
}

// A requester follows these on My Requests; the rest live in History.
export const closeReasonLabels: Record<CloseReason, string> = {
  REQUESTER_CANCELLED: 'Cancelled by requester',
  RESERVATION_TIMEOUT: 'Credit reservation timed out',
  INSUFFICIENT_CREDITS: 'Not enough credits',
  INVALID_REWARD: 'Invalid reward',
  DEADLINE_PASSED: 'Deadline passed',
  DISPUTE_RESOLVED: 'Dispute resolved',
}

export const activeStatuses: RequestStatus[] = [
  'PENDING',
  'CREATED',
  'ACCEPTED',
  'COLLECTED',
  'ARRIVED',
]

export const finishedStatuses: RequestStatus[] = [
  'COMPLETED',
  'CANCELLED',
  'EXPIRED',
  'REJECTED',
]

export const progressSteps = [
  'Created',
  'Accepted',
  'Collected',
  'Arrived',
  'Completed',
] as const

// How many progress steps the request has reached (0: not yet created).
export function progressOf(status: RequestStatus): number {
  switch (status) {
    case 'CREATED':
      return 1
    case 'ACCEPTED':
      return 2
    case 'COLLECTED':
      return 3
    case 'ARRIVED':
      return 4
    case 'COMPLETED':
      return 5
    default:
      return 0
  }
}

export function isTerminal(status: RequestStatus): boolean {
  return (
    status === 'COMPLETED' ||
    status === 'CANCELLED' ||
    status === 'EXPIRED' ||
    status === 'REJECTED'
  )
}

export function credits(n: number): string {
  return `${n} ${n === 1 ? 'Credit' : 'Credits'}`
}

const dateTime = new Intl.DateTimeFormat('en-SG', {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

// "in 4 hours" / "2 hours ago" — coarse on purpose, for deadlines.
export function relativeTime(iso: string, now = Date.now()): string {
  const diffMs = new Date(iso).getTime() - now
  const abs = Math.abs(diffMs)
  const minutes = Math.round(abs / 60_000)
  let text: string
  if (minutes < 1) text = 'less than a minute'
  else if (minutes < 60) text = `${minutes} min`
  else if (minutes < 60 * 24) {
    const hours = Math.round((minutes / 60) * 2) / 2
    text = `${hours} ${hours === 1 ? 'hour' : 'hours'}`
  } else {
    const days = Math.round(minutes / (60 * 24))
    text = `${days} ${days === 1 ? 'day' : 'days'}`
  }
  if (minutes < 1) return diffMs >= 0 ? 'in under a minute' : 'just now'
  return diffMs >= 0 ? `in ${text}` : `${text} ago`
}

// The line shown under a request's deadline, depending on where it ended up.
export function deadlineLine(request: RequestSummary): string {
  switch (request.status) {
    case 'PENDING':
    case 'CREATED':
      return `${formatDateTime(request.deadline)} (${relativeTime(request.deadline)})`
    case 'EXPIRED':
      return `Expired ${formatDateTime(request.deadline)}`
    default:
      return formatDateTime(request.deadline)
  }
}

// When an ARRIVED request completes by itself, or null if it isn't waiting.
export function autoCompleteAt(request: RequestDetail): Date | null {
  if (request.status !== 'ARRIVED' || !request.arrivedAt) return null
  return new Date(new Date(request.arrivedAt).getTime() + AUTO_COMPLETE_MS)
}
