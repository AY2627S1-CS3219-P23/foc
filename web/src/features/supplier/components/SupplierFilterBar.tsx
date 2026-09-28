// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26/27 (Claude Code, Sonnet 5).
// Scope: search + category filter + sort controls, implementing F2.1
// (search by name) and F2.2 (filter by category), per
// web/docs/wireframes/suppliers.png.
// 2026-09-26: removed the zone filter — the team dropped the zone
// feature, and the dropdown had nothing to populate it with.
// 2026-09-26: split the "Category:" label out of the <select>'s own
// options into a static prefix (author decision) — a native select
// always shows its own selected option's text when collapsed, so
// "Category: All"/"Category: Coffee" couldn't both collapse correctly
// and list cleanly as "All"/"Coffee" while the label lived inside the
// options themselves. Search field narrowed (basis instead of flex-1)
// so the wider category control fits without crowding on small widths.
// Given a fixed width (rather than sizing to the selected option's
// text) so the control doesn't resize, and the search box next to it
// doesn't jump, as the selected category changes length.
// 2026-09-26: the <select> itself was previously only the narrow strip
// after the "Category:" label, so (a) the browser's dropdown popup —
// sized to the <select>'s own box — was narrower than the full field,
// and (b) clicking the "Category:" label text did nothing, since it
// wasn't part of the interactive element. Fixed by stretching the
// <select> to cover the whole field (invisible; `opacity-0`) and
// rendering the visible "Category: X" text as a separate, non-interactive
// overlay — the standard technique for a custom-styled native select.
// 2026-09-27: added a sort control (PR #134 Copilot review: the
// backend supported `sort` via Pageable but nothing let the user
// request one) with a "Nearest to Me" option (team decision: sort by
// distance from the user's current location). Extracted the
// label+overlay+chevron pattern into `LabeledSelect` since Category
// and Sort both need it. Added an optional `locationNotice` line for
// geolocation status (requesting/denied) since that's specific to the
// "Nearest to Me" option, not a generic filter-bar concern.
// Reviewed by: [pending]

interface LabeledSelectOption {
  value: string
  label: string
}

interface LabeledSelectProps {
  label: string
  ariaLabel: string
  value: string
  onChange: (value: string) => void
  options: LabeledSelectOption[]
}

function LabeledSelect({ label, ariaLabel, value, onChange, options }: LabeledSelectProps) {
  const selectedLabel = options.find((o) => o.value === value)?.label ?? options[0]?.label ?? ''

  return (
    <div className="relative flex w-full items-center gap-1.5 rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-700 focus-within:border-gray-400 sm:w-44">
      <span className="pointer-events-none">
        <span className="text-gray-500">{label}:</span> <span className="text-gray-900">{selectedLabel}</span>
      </span>
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className="pointer-events-none ml-auto h-4 w-4 shrink-0 text-gray-400"
      >
        <path fill="currentColor" d="M5.25 7.5 10 12.25 14.75 7.5H5.25Z" />
      </svg>
      <select
        aria-label={ariaLabel}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  )
}

interface SupplierFilterBarProps {
  query: string
  onQueryChange: (value: string) => void
  category: string
  onCategoryChange: (value: string) => void
  categories: string[]
  sort: string
  onSortChange: (value: string) => void
  locationNotice?: string | null
}

export function SupplierFilterBar({
  query,
  onQueryChange,
  category,
  onCategoryChange,
  categories,
  sort,
  onSortChange,
  locationNotice,
}: SupplierFilterBarProps) {
  return (
    <div className="space-y-1.5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="search"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Search suppliers..."
          className="min-w-0 flex-1 basis-1/2 rounded-md border border-gray-200 px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
        />

        <LabeledSelect
          label="Category"
          ariaLabel="Filter by category"
          value={category}
          onChange={onCategoryChange}
          options={[{ value: '', label: 'All' }, ...categories.map((c) => ({ value: c, label: c }))]}
        />

        <LabeledSelect
          label="Sort"
          ariaLabel="Sort suppliers"
          value={sort}
          onChange={onSortChange}
          options={[
            { value: 'name,asc', label: 'Name (A–Z)' },
            { value: 'name,desc', label: 'Name (Z–A)' },
            { value: 'distance', label: 'Nearest to Me' },
          ]}
        />
      </div>

      {locationNotice && <p className="text-xs text-gray-500">{locationNotice}</p>}
    </div>
  )
}
