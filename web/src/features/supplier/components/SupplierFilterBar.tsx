// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: search + category filter controls, implementing F2.1
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
// Reviewed by: [pending]

interface SupplierFilterBarProps {
  query: string
  onQueryChange: (value: string) => void
  category: string
  onCategoryChange: (value: string) => void
  categories: string[]
}

export function SupplierFilterBar({
  query,
  onQueryChange,
  category,
  onCategoryChange,
  categories,
}: SupplierFilterBarProps) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <input
        type="search"
        value={query}
        onChange={(e) => onQueryChange(e.target.value)}
        placeholder="Search suppliers..."
        className="min-w-0 flex-1 basis-2/3 rounded-md border border-gray-200 px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
      />

      <div className="relative flex w-full items-center gap-1.5 rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-700 focus-within:border-gray-400 sm:w-44">
        <span className="pointer-events-none">
          <span className="text-gray-500">Category:</span>{' '}
          <span className="text-gray-900">{category || 'All'}</span>
        </span>
        <svg
          aria-hidden="true"
          viewBox="0 0 20 20"
          className="pointer-events-none ml-auto h-4 w-4 shrink-0 text-gray-400"
        >
          <path
            fill="currentColor"
            d="M5.25 7.5 10 12.25 14.75 7.5H5.25Z"
          />
        </svg>
        <select
          aria-label="Filter by category"
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
        >
          <option value="">All</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
