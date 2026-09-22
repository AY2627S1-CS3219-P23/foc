// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: supplier detail panel, per web/docs/wireframes/suppliers.png.
// `onSelect` is optional so this can be reused later inside the Order
// Service's "create request" flow (pickup-location picker) without
// change; the pure Supplier browse page just omits it.
// Reviewed by: [pending]

import type { Supplier, Zone } from '../types'

interface SupplierDetailPanelProps {
  supplier: Supplier
  zone?: Zone
  onSelect?: () => void
  isAdmin?: boolean // TEMPORARY — see suppliers.tsx for why
  onEdit?: () => void
  onDelete?: () => void
}

export function SupplierDetailPanel({
  supplier,
  zone,
  onSelect,
  isAdmin,
  onEdit,
  onDelete,
}: SupplierDetailPanelProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <p className="text-xs font-medium text-gray-400">Active supplier detail</p>
      <h2 className="mt-1 text-xl font-semibold text-gray-900">{supplier.name}</h2>

      {supplier.imageUrl ? (
        <img
          src={supplier.imageUrl}
          alt={supplier.name}
          className="mt-4 h-40 w-full rounded-md object-cover"
        />
      ) : (
        <div className="mt-4 flex h-40 w-full items-center justify-center rounded-md border border-dashed border-gray-200 text-xs text-gray-400">
          No image
        </div>
      )}

      <dl className="mt-4 space-y-2 text-sm">
        <div>
          <dt className="inline font-medium text-gray-900">Location: </dt>
          <dd className="inline text-gray-600">
            {supplier.location}
            {zone ? `, ${zone.name}` : ''}
          </dd>
        </div>
        <div>
          <dt className="inline font-medium text-gray-900">Opening hours: </dt>
          <dd className="inline text-gray-600">
            {supplier.openingTime}–{supplier.closingTime}
          </dd>
        </div>
        <div>
          <dt className="inline font-medium text-gray-900">Categories: </dt>
          <dd className="inline text-gray-600">{supplier.categories.join(', ')}</dd>
        </div>
      </dl>

      {supplier.description && (
        <p className="mt-3 text-sm text-gray-600">{supplier.description}</p>
      )}

      <div className="mt-5 flex flex-col gap-2">
        {onSelect && (
          <button
            type="button"
            onClick={onSelect}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            Select this supplier
          </button>
        )}

        {isAdmin && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onEdit}
              className="flex-1 rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Edit
            </button>
            <button
              type="button"
              onClick={onDelete}
              className="flex-1 rounded-md border border-red-200 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
            >
              Delete
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
