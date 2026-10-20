// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-18.
// Scope: the state filter chips on My Requests and Request History.
// Each chip is one backend query, so paging stays exact (the author's
// choice over merging several calls).
// 2026-10-20: a chip stands for a set of states — one state, or a group
// such as "all active" / "all finished" — which order-service takes as a
// comma-separated state list (the author's decision).
// Author review: [pending]

import type { RequestStatus } from '../types'

export interface FilterOption {
  key: string
  label: string
  states: RequestStatus[]
}

export function StateFilter({
  options,
  value,
  onChange,
  label,
}: {
  options: FilterOption[]
  value: string // the selected option's key
  onChange: (next: FilterOption) => void
  label: string
}) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const selected = option.key === value
        return (
          <button
            key={option.key}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option)}
            className={`rounded-full border px-3 py-1 text-xs font-medium ${
              selected
                ? 'border-gray-900 bg-gray-900 text-white'
                : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
