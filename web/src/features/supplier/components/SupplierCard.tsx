// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-27 (Claude Code, Sonnet 5).
// Scope: one supplier's card in the browse grid, per
// web/docs/wireframes/suppliers.png.
// 2026-09-27: added an optional `distanceLabel` — team decision to
// support "sort by distance from the user's current location"; without
// showing the actual distance, a nearest-first re-ordering would look
// unexplained to whoever is testing/using the feature.
// Reviewed by: [pending]

import type { Supplier } from '../types'

interface SupplierCardProps {
  supplier: Supplier
  selected: boolean
  onSelect: () => void
  distanceLabel?: string
}

export function SupplierCard({ supplier, selected, onSelect, distanceLabel }: SupplierCardProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={`w-full rounded-lg border p-4 text-left transition-colors ${
        selected
          ? 'border-gray-900 bg-white shadow-sm'
          : 'border-gray-200 bg-white hover:border-gray-300'
      }`}
    >
      <h3 className="font-semibold text-gray-900">{supplier.name}</h3>

      <div className="mt-1 flex flex-wrap gap-1.5">
        {supplier.categories.map((category) => (
          <span
            key={category}
            className="rounded-md border border-gray-200 px-2 py-0.5 text-xs text-gray-600"
          >
            {category}
          </span>
        ))}
      </div>

      {supplier.description && (
        <p className="mt-2 line-clamp-2 text-sm text-gray-600">
          {supplier.description}
        </p>
      )}

      <p className="mt-2 text-xs text-gray-400">
        {supplier.openingTime}–{supplier.closingTime}
        {distanceLabel && <> · {distanceLabel}</>}
      </p>
    </button>
  )
}
