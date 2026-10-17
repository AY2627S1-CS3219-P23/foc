// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credits page's three balance cards, per
// web/docs/wireframes/credit-history.png. Team decision: the
// wireframe's "Total lifetime earned" card is replaced by Total
// (available + reserved), since the read API has no lifetime figure.
// 2026-10-17, issue #196: the Available card's line for the soonest
// credits to expire (team decision: on the cards, in Singapore dates).
// Author review: Ryan Ang, pending pull request review.

import { expiryLine, type NextExpiry } from '../expiry'
import type { BalanceState } from '../useCreditBalance'

const CARDS = [
  {
    key: 'available',
    title: 'Available credits',
    hint: 'Usable for creating requests',
  },
  {
    key: 'reserved',
    title: 'Reserved credits',
    hint: 'Locked in pending active errands',
  },
  { key: 'total', title: 'Total credits', hint: 'Available plus reserved' },
] as const

export function BalanceCards({
  state,
  next = null,
}: {
  state: BalanceState
  // the soonest credits to expire; null when none will, or not loaded
  next?: NextExpiry | null
}) {
  return (
    <div>
      {state.status === 'error' && (
        <p role="alert" className="mb-3 text-sm text-red-700">
          {state.message}
        </p>
      )}
      {/* three across on desktop; on mobile one compact row, as drawn */}
      <dl className="grid grid-cols-3 gap-2 md:gap-4">
        {CARDS.map((card) => (
          <div
            key={card.key}
            className="rounded-lg border border-gray-200 bg-white p-3 text-center shadow-sm md:p-5 md:text-left"
          >
            <dt className="text-xs font-medium tracking-wide text-gray-500 md:uppercase">
              {card.title}
            </dt>
            <dd className="mt-1 text-xl font-semibold text-gray-900 md:text-3xl">
              {state.status === 'ready' ? state.balance[card.key] : '—'}
            </dd>
            <dd className="mt-1 hidden text-xs text-gray-500 md:block">
              {card.hint}
            </dd>
            {card.key === 'available' && next && (
              <dd className="mt-1 text-xs font-medium text-amber-700">
                {expiryLine(next)}
              </dd>
            )}
          </div>
        ))}
      </dl>
    </div>
  )
}
