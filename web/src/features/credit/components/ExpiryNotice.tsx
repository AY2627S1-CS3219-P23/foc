// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-17, issue #196.
// Scope: the credits page's warning when credits expire within 7 days.
// Team decision: shown only then, above the transactions, in the
// team's wording ("3 credits expire on 12 Jan. Use them on a request
// before then.").
// Author review: Ryan Ang, pending pull request review.

import type { NextExpiry } from '../expiry'
import { credits } from '../poolFormat'

export function ExpiryNotice({ next }: { next: NextExpiry }) {
  const verb = next.amount === 1 ? 'expires' : 'expire'
  const when = next.date ? `on ${next.date}` : 'tonight'
  const them = next.amount === 1 ? 'it' : 'them'
  return (
    <p
      role="status"
      className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
    >
      {credits(next.amount)} {verb} {when}. Use {them} on a request before then.
    </p>
  )
}
