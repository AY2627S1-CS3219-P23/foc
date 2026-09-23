// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: search + category/zone filter controls, implementing F2.1
// (search by name) and F2.2 (filter by category and zone), per
// web/docs/wireframes/suppliers.png.
// Reviewed by: [pending]

import type { Zone } from '../types'

interface SupplierFilterBarProps {
  query: string
  onQueryChange: (value: string) => void
  category: string
  onCategoryChange: (value: string) => void
  categories: string[]
  zoneCode: string
  onZoneChange: (value: string) => void
  zones: Zone[]
}

export function SupplierFilterBar({
  query,
  onQueryChange,
  category,
  onCategoryChange,
  categories,
  zoneCode,
  onZoneChange,
  zones,
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

      <select
        value={zoneCode}
        onChange={(e) => onZoneChange(e.target.value)}
        className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-700 focus:border-gray-400 focus:outline-none"
      >
        <option value="">Zone: All</option>
        {zones.map((z) => (
          <option key={z.code} value={z.code}>
            {z.name}
          </option>
        ))}
      </select>
    </div>
  )
}
