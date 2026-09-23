// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: Suppliers page (browse/search/filter + admin CRUD), per
// web/docs/wireframes/suppliers.png and add-edit-supplier.png.
// Implements F1.2, F1.2.1, F2.1, F2.2, F2.2.1, F1.1-F1.1.4.
//
// TEMPORARY: admin gating is a hardcoded constant below because
// src/shared/auth/ doesn't exist yet (see its README). Replace
// IS_ADMIN with the real role check once User Service auth lands —
// search this file for "TEMPORARY" when that happens.
// Reviewed by: [pending]

import { Fragment, useEffect, useMemo, useState } from 'react'

import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  listZones,
  updateSupplier,
} from '@/features/supplier/api'
import { DeleteSupplierModal } from '@/features/supplier/components/DeleteSupplierModal'
import { SupplierCard } from '@/features/supplier/components/SupplierCard'
import { SupplierDetailPanel } from '@/features/supplier/components/SupplierDetailPanel'
import { SupplierFilterBar } from '@/features/supplier/components/SupplierFilterBar'
import { SupplierFormModal } from '@/features/supplier/components/SupplierFormModal'
import type { Supplier, SupplierInput, Zone } from '@/features/supplier/types'
import { ApiError } from '@/lib/api/http'

// TEMPORARY — see file header.
const IS_ADMIN = true

export function Suppliers() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [zones, setZones] = useState<Zone[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [zoneCode, setZoneCode] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const [formOpenFor, setFormOpenFor] = useState<Supplier | 'new' | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Supplier | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function loadSuppliers() {
      setLoading(true)
      setError(null)
      try {
        const [supplierResult, zoneResult] = await Promise.all([
          listSuppliers({ q: query, category, zoneCode }),
          listZones(),
        ])
        if (cancelled) return
        setSuppliers(supplierResult)
        setZones(zoneResult)
      } catch (err) {
        if (cancelled) return
        setError(
          err instanceof ApiError
            ? err.message
            : 'Could not load suppliers. Try again.',
        )
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadSuppliers()

    return () => {
      cancelled = true
    }
  }, [query, category, zoneCode])

  const categories = useMemo(
    () => Array.from(new Set(suppliers.flatMap((s) => s.categories))).sort(),
    [suppliers],
  )

  const selected = suppliers.find((s) => s.id === selectedId) ?? null
  const selectedZone = selected ? zones.find((z) => z.code === selected.zoneCode) : undefined

  async function handleSave(input: SupplierInput) {
    setSaving(true)
    try {
      if (formOpenFor && formOpenFor !== 'new') {
        const updated = await updateSupplier(formOpenFor.id, input)
        setSuppliers((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
      } else {
        const created = await createSupplier(input)
        setSuppliers((prev) => [...prev, created])
      }
      setFormOpenFor(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save supplier.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return
    setSaving(true)
    try {
      await deleteSupplier(pendingDelete.id)
      setSuppliers((prev) => prev.filter((s) => s.id !== pendingDelete.id))
      if (selectedId === pendingDelete.id) setSelectedId(null)
      setPendingDelete(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete supplier.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-xl font-semibold text-gray-900">Active Campus Suppliers</h1>
        {IS_ADMIN && (
          <button
            type="button"
            onClick={() => setFormOpenFor('new')}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            + Add Supplier
          </button>
        )}
      </div>

      <SupplierFilterBar
        query={query}
        onQueryChange={setQuery}
        category={category}
        onCategoryChange={setCategory}
        categories={categories}
        zoneCode={zoneCode}
        onZoneChange={setZoneCode}
        zones={zones}
      />

      {error && (
        <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className={`grid gap-6 ${selected ? 'lg:grid-cols-[1fr_20rem]' : ''}`}>
        <div>
          {loading ? (
            <p className="text-sm text-gray-500">Loading suppliers...</p>
          ) : suppliers.length === 0 ? (
            <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
              <p className="font-medium text-gray-900">
                No other suppliers match your filters.
              </p>
              <p className="mt-1 text-sm text-gray-500">
                Try adjusting your search query or zone settings.
              </p>
            </div>
          ) : (
            <div className="grid grid-flow-dense gap-3 sm:grid-cols-2">
              {suppliers.map((supplier) => (
                <Fragment key={supplier.id}>
                  <SupplierCard
                    supplier={supplier}
                    selected={supplier.id === selectedId}
                    onSelect={() =>
                      setSelectedId(supplier.id === selectedId ? null : supplier.id)
                    }
                  />
                  {/* Below lg, the sidebar column collapses away, so show
                      the panel inline right after its card instead of
                      letting it fall to the end of the grid. */}
                  {selected && supplier.id === selectedId && (
                    <div className="col-span-full lg:hidden">
                      <SupplierDetailPanel
                        supplier={selected}
                        zone={selectedZone}
                        isAdmin={IS_ADMIN}
                        onEdit={() => setFormOpenFor(selected)}
                        onDelete={() => setPendingDelete(selected)}
                      />
                    </div>
                  )}
                </Fragment>
              ))}
            </div>
          )}
        </div>

        {/* Desktop: side panel column. */}
        {selected && (
          <div className="hidden lg:block">
            <SupplierDetailPanel
              supplier={selected}
              zone={selectedZone}
              isAdmin={IS_ADMIN}
              onEdit={() => setFormOpenFor(selected)}
              onDelete={() => setPendingDelete(selected)}
            />
          </div>
        )}
      </div>

      {formOpenFor && (
        <SupplierFormModal
          initial={formOpenFor === 'new' ? undefined : formOpenFor}
          zones={zones}
          onCancel={() => setFormOpenFor(null)}
          onSave={handleSave}
          saving={saving}
        />
      )}

      {pendingDelete && (
        <DeleteSupplierModal
          supplier={pendingDelete}
          onCancel={() => setPendingDelete(null)}
          onConfirm={handleDelete}
          deleting={saving}
        />
      )}
    </div>
  )
}
