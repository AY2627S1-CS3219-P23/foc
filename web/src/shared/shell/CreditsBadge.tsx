// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: placeholder credits slot in the top bar (wireframes show
// "Available: N Credits").
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-16, Claude Code (Opus 5.5), issue #174: the placeholder
// replaced by the live available balance from the shared poll
// (CreditBalanceProvider), as a link to /credits (team decision: the
// badge is the way into the credits page). "Credits: N", as
// credit-history.png draws it; a dash while logged out, loading, or
// when the balance cannot be read.
// Author review: Ryan Ang, pending pull request review.

import { Link } from 'react-router'

import { useCreditBalance } from '@/features/credit/useCreditBalance'

export function CreditsBadge() {
  const { state } = useCreditBalance()
  const available =
    state?.status === 'ready' ? String(state.balance.available) : '—'
  const title =
    state?.status === 'error'
      ? 'Credit balance unavailable'
      : 'Credit balance — view transactions'

  return (
    <Link
      to="/credits"
      title={title}
      className="rounded-md border border-gray-300 bg-white px-3 py-1 text-sm font-medium whitespace-nowrap hover:bg-gray-50"
    >
      Credits: <span className="font-semibold">{available}</span>
    </Link>
  )
}
