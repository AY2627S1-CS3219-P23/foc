// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-17, issue #196.
// Scope: how the credits page reads GET /credits/me/expiries: items
// grouped by their Singapore date, the soonest group for the balance
// card, and whether it falls in the 7-day notice window. Team decisions:
// dates in Asia/Singapore, the 7-day notice, and an item already past
// its date shown as "expires tonight". The tool's reading: anything
// dated today or earlier in Singapore goes in tonight's 00:00 expiry run,
// so it all reads "tonight".
// Author review: Ryan Ang, pending pull request review.

import { credits } from './poolFormat'
import type { CreditExpiry } from './types'

// the notice shows when the soonest credits expire within this many days
export const EXPIRY_NOTICE_DAYS = 7

// yyyy-mm-dd in Singapore, which sorts and compares as text
const sgtDay = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Singapore',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

// "12 Jan 2027"
const sgtShortDate = new Intl.DateTimeFormat('en-SG', {
  timeZone: 'Asia/Singapore',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const dayKey = (instant: Date) => sgtDay.format(instant)

// whole days from one yyyy-mm-dd to another
function daysBetween(from: string, to: string): number {
  return (Date.parse(to) - Date.parse(from)) / 86_400_000
}

export interface NextExpiry {
  amount: number
  // the Singapore date, as "12 Jan 2027"; null when it is tonight
  date: string | null
  // within EXPIRY_NOTICE_DAYS of today, tonight included
  soon: boolean
}

/**
 * The soonest credits to expire: every item on the soonest Singapore
 * date added up, or null when nothing will expire. An item dated today
 * or earlier is swept at the coming midnight, so those count as one
 * group: tonight.
 */
export function nextExpiry(
  items: CreditExpiry[],
  now: Date = new Date(),
): NextExpiry | null {
  if (items.length === 0) return null
  const today = dayKey(now)
  // past dates fold into today: they all go tonight
  const days = items.map((item) => {
    const day = dayKey(new Date(item.expiresAt))
    return day < today ? today : day
  })
  const soonest = days.reduce((a, b) => (b < a ? b : a))
  const amount = items.reduce(
    (sum, item, i) => (days[i] === soonest ? sum + item.amount : sum),
    0,
  )
  const tonight = soonest === today
  const first = items[days.indexOf(soonest)]
  return {
    amount,
    date: tonight ? null : sgtShortDate.format(new Date(first.expiresAt)),
    soon: daysBetween(today, soonest) <= EXPIRY_NOTICE_DAYS,
  }
}

// "5 credits expire by 12 Jan 2027", or "… tonight" for the coming sweep
export function expiryLine(next: NextExpiry): string {
  const verb = next.amount === 1 ? 'expires' : 'expire'
  return `${credits(next.amount)} ${verb} ${next.date ? `by ${next.date}` : 'tonight'}`
}
