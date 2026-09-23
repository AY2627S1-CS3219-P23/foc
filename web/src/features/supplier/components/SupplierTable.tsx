// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: supplier list for the Admin Dashboard — a table at md+ and
// stacked cards below, per web/docs/wireframes/admin-dashboard.png
// (Suppliers section). New file in the supplier feature, added for the
// admin page; no existing supplier code changed.
// Reviewed by: [pending]

import type { Supplier, Zone } from '../types'

interface SupplierTableProps {
  suppliers: Supplier[]
  zones: Zone[]
  onEdit: (supplier: Supplier) => void
  onDelete: (supplier: Supplier) => void
}

export function SupplierTable({
  suppliers,
  zones,
  onEdit,
  onDelete,
}: SupplierTableProps) {
  const zoneName = (code: string) =>
    zones.find((z) => z.code === code)?.name ?? code
  const hours = (s: Supplier) => `${s.openingTime}–${s.closingTime}`

  function actions(supplier: Supplier) {
    return (
      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => onEdit(supplier)}
          className="text-sm font-medium text-gray-900 hover:underline"
        >
          Edit
        </button>
        <button
          type="button"
          onClick={() => onDelete(supplier)}
          className="text-sm font-medium text-red-600 hover:underline"
        >
          Delete
        </button>
      </div>
    )
  }

  return (
    <>
      {/* Desktop / tablet */}
      <div className="hidden overflow-x-auto rounded-lg border border-gray-200 bg-white md:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
            <tr>
              <th scope="col" className="px-4 py-3">
                Name
              </th>
              <th scope="col" className="px-4 py-3">
                Category
              </th>
              <th scope="col" className="px-4 py-3">
                Zone
              </th>
              <th scope="col" className="px-4 py-3">
                Location
              </th>
              <th scope="col" className="px-4 py-3">
                Hours
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {suppliers.map((supplier) => (
              <tr key={supplier.id}>
                <td className="px-4 py-3 font-medium text-gray-900">
                  {supplier.name}
                </td>
                <td className="px-4 py-3 text-gray-600">
                  {supplier.categories.join(', ')}
                </td>
                <td className="px-4 py-3 text-gray-600">
                  {zoneName(supplier.zoneCode)}
                </td>
                <td className="px-4 py-3 text-gray-600">{supplier.location}</td>
                <td className="whitespace-nowrap px-4 py-3 text-gray-600">
                  {hours(supplier)}
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end">{actions(supplier)}</div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile */}
      <ul className="space-y-3 md:hidden">
        {suppliers.map((supplier) => (
          <li
            key={supplier.id}
            className="rounded-lg border border-gray-200 bg-white p-4"
          >
            <p className="font-medium text-gray-900">{supplier.name}</p>
            <p className="text-sm text-gray-600">
              {supplier.location} • {hours(supplier)}
            </p>
            <p className="text-xs text-gray-400">
              {[...supplier.categories, zoneName(supplier.zoneCode)].join(
                ' • ',
              )}
            </p>
            <div className="mt-3">{actions(supplier)}</div>
          </li>
        ))}
      </ul>
    </>
  )
}
