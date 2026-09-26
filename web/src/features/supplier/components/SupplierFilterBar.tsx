// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: search + category filter controls, implementing F2.1
// (search by name) and F2.2 (filter by category), per
// web/docs/wireframes/suppliers.png.
// 2026-09-26: removed the zone filter — the team dropped the zone
// feature, and the dropdown had nothing to populate it with.
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
        className="flex-1 rounded-md border border-gray-200 px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
      />

      <select
        value={category}
        onChange={(e) => onCategoryChange(e.target.value)}
        className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-700 focus:border-gray-400 focus:outline-none"
      >
        <option value="">Category: All</option>
        {categories.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
    </div>
  )
}
