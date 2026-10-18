// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-18.
// Scope: the state filter chips on My Requests and Request History.
// order-service filters a list by one state (GET /requests/mine?state=),
// so each chip is one backend query and paging stays exact (the author's
// choice over merging several calls).
// Author review: [pending]

import { statusLabels } from '../status'
import type { RequestStatus } from '../types'

export function StateFilter({
  options,
  value,
  onChange,
  label,
}: {
  // undefined is "All"
  options: (RequestStatus | undefined)[]
  value: RequestStatus | undefined
  onChange: (next: RequestStatus | undefined) => void
  label: string
}) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const selected = option === value
        return (
          <button
            key={option ?? 'ALL'}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option)}
            className={`rounded-full border px-3 py-1 text-xs font-medium ${
              selected
                ? 'border-gray-900 bg-gray-900 text-white'
                : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50'
            }`}
          >
            {option ? statusLabels[option] : 'All'}
          </button>
        )
      })}
    </div>
  )
}
