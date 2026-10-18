// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the transaction list, per web/docs/wireframes/credit-history.png:
// a table on desktop (date/time, type, description, amount) and one
// card per row on mobile. Team decision: no running-balance column,
// since the history rows carry no balance.
// Same day, author's request: a loss's amount in red, as a gain's is
// in green.
// Author review: Ryan Ang, pending pull request review.

import { displayEntry, formatOccurredAt } from '../history'
import type { CreditHistoryEntry } from '../types'

function TypeChip({ label }: { label: string }) {
  return (
    <span className="inline-block rounded-full border border-gray-300 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-gray-700">
      {label}
    </span>
  )
}

function Amount({ text, gain }: { text: string; gain: boolean }) {
  return (
    <span
      className={`font-semibold whitespace-nowrap ${gain ? 'text-green-700' : 'text-red-700'}`}
    >
      {text}
    </span>
  )
}

export function HistoryList({ entries }: { entries: CreditHistoryEntry[] }) {
  const rows = entries.map((entry) => ({
    entry,
    when: formatOccurredAt(entry.occurredAt),
    ...displayEntry(entry),
  }))

  return (
    <>
      <table className="hidden w-full text-left text-sm md:table">
        <thead>
          <tr className="border-b border-gray-200 text-xs font-semibold tracking-wide text-gray-600 uppercase">
            <th scope="col" className="py-3 pr-4">
              Date / time
            </th>
            <th scope="col" className="py-3 pr-4">
              Type
            </th>
            <th scope="col" className="py-3 pr-4">
              Description
            </th>
            <th scope="col" className="py-3 text-right">
              Amount
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.entry.id} className="border-b border-gray-100">
              <td className="py-3 pr-4 whitespace-nowrap text-gray-600">
                {row.when}
              </td>
              <td className="py-3 pr-4">
                <TypeChip label={row.label} />
              </td>
              <td className="py-3 pr-4 text-gray-900">{row.description}</td>
              <td className="py-3 text-right">
                <Amount text={row.signedAmount} gain={row.gain} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ul className="space-y-2 md:hidden">
        {rows.map((row) => (
          <li
            key={row.entry.id}
            className="flex items-start justify-between gap-3 rounded-lg border border-gray-200 bg-white p-3"
          >
            <div className="min-w-0">
              <p className="text-sm font-medium break-words text-gray-900">
                {row.description}
              </p>
              <p className="mt-0.5 text-xs text-gray-500">{row.when}</p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <TypeChip label={row.label} />
              <Amount text={row.signedAmount} gain={row.gain} />
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}
