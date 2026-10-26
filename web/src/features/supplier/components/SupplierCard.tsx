// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-10-26 (Claude Code, Sonnet 5.5).
// Scope: one supplier's card in the browse grid, per
// web/docs/wireframes/suppliers.png.
// 2026-09-27: added an optional `distanceLabel` — team decision to
// support "sort by distance from the user's current location"; without
// showing the actual distance, a nearest-first re-ordering would look
// unexplained to whoever is testing/using the feature.
// 2026-10-26, issue #225 (N8): bookmark flag in the card's top-right
// corner (yellow = bookmarked, hollow = not), per the author's marked-up
// wireframe. The flag is a sibling of the select button, not a child —
// a button inside a button is invalid HTML and would make a flag click
// also select the card.
// 2026-10-26: wrapper and select button are h-full, so a card still stretches to
// its grid row (it was the grid item itself before the flag wrapper); the
// button is a top-aligned flex column, since a button centres its content
// vertically and a short card (no categories) would otherwise float down.
// Reviewed by: [pending]

import type { Supplier } from '../types'

interface SupplierCardProps {
  supplier: Supplier
  selected: boolean
  onSelect: () => void
  distanceLabel?: string
  bookmarked?: boolean
  onToggleBookmark?: () => void
  bookmarkBusy?: boolean
}

export function SupplierCard({
  supplier,
  selected,
  onSelect,
  distanceLabel,
  bookmarked = false,
  onToggleBookmark,
  bookmarkBusy = false,
}: SupplierCardProps) {
  return (
    <div className="relative h-full">
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={selected}
        className={`flex h-full w-full flex-col justify-start rounded-lg border p-4 text-left transition-colors ${
          selected
            ? 'border-gray-900 bg-white shadow-sm'
            : 'border-gray-200 bg-white hover:border-gray-300'
        }`}
      >
        <h3 className="pr-8 font-semibold text-gray-900">{supplier.name}</h3>

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

      {onToggleBookmark && (
        <button
          type="button"
          onClick={onToggleBookmark}
          disabled={bookmarkBusy}
          aria-pressed={bookmarked}
          aria-label={
            bookmarked
              ? `Remove bookmark: ${supplier.name}`
              : `Bookmark: ${supplier.name}`
          }
          className="absolute top-3 right-3 rounded p-0.5 text-gray-900 hover:bg-gray-100 disabled:opacity-50"
        >
          <svg
            viewBox="0 0 24 24"
            className="h-5 w-5"
            fill={bookmarked ? '#facc15' : 'none'}
            stroke="currentColor"
            strokeWidth="2"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M6 3h12v18l-6-4-6 4z" />
          </svg>
        </button>
      )}
    </div>
  )
}
