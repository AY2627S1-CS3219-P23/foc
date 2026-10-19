// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: status labels/colours, the five-step progress tracker and the
// date/credit formatting shared by the order pages.
// Author review: [pending]

import type { Order, OrderStatus } from './types'

export const AUTO_COMPLETE_MS = 60 * 60 * 1000 // 1 hour after courier arrival

export const statusLabels: Record<OrderStatus, string> = {
  OPEN: 'Open',
  ACCEPTED: 'Accepted',
  PICKED_UP: 'Collected',
  DELIVERED: 'Delivered',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  DISPUTED: 'In dispute',
}

export const statusChipClasses: Record<OrderStatus, string> = {
  OPEN: 'bg-blue-50 text-blue-700',
  ACCEPTED: 'bg-gray-100 text-gray-700',
  PICKED_UP: 'bg-amber-50 text-amber-700',
  DELIVERED: 'bg-purple-50 text-purple-700',
  COMPLETED: 'bg-green-50 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
  EXPIRED: 'bg-gray-100 text-gray-500',
  DISPUTED: 'bg-red-50 text-red-700',
}

export const progressSteps = [
  'Created',
  'Accepted',
  'Collected',
  'Delivered',
  'Completed',
] as const

// How many steps of progressSteps are reached (1 = just created).
export function progressOf(status: OrderStatus): number {
  switch (status) {
    case 'OPEN':
      return 1
    case 'ACCEPTED':
      return 2
    case 'PICKED_UP':
      return 3
    case 'DELIVERED':
    case 'DISPUTED':
      return 4
    case 'COMPLETED':
      return 5
    default:
      return 0
  }
}

export function isTerminal(status: OrderStatus): boolean {
  return (
    status === 'COMPLETED' || status === 'CANCELLED' || status === 'EXPIRED'
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

// The line shown under an order's deadline, depending on where it ended up.
export function deadlineLine(order: Order): string {
  switch (order.status) {
    case 'OPEN':
      return `${formatDateTime(order.deadline)} (${relativeTime(order.deadline)})`
    case 'COMPLETED':
      return `Completed ${formatDateTime(order.updatedAt)}`
    case 'CANCELLED':
      return `Cancelled ${formatDateTime(order.updatedAt)}`
    case 'EXPIRED':
      return `Expired ${formatDateTime(order.deadline)}`
    default:
      return `${formatDateTime(order.deadline)}`
  }
}

// When a DELIVERED order completes by itself, or null if it isn't waiting.
export function autoCompleteAt(order: Order): Date | null {
  if (order.status !== 'DELIVERED' || !order.deliveredAt) return null
  return new Date(new Date(order.deliveredAt).getTime() + AUTO_COMPLETE_MS)
}
