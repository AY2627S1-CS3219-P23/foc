// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: Admin Dashboard "Suppliers" section — list with Edit/Delete and
// "+ Add Supplier", per web/docs/wireframes/admin-dashboard.png. Reuses
// the supplier feature's existing api.ts and form/delete modals (as
// routes/suppliers.tsx does); new file, no existing supplier code changed.
// Reviewed by: [pending]

import { useEffect, useState } from 'react'

import { ApiError } from '@/lib/api/http'
import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  listZones,
  updateSupplier,
} from '../api'
import type { Supplier, SupplierInput, Zone } from '../types'
import { DeleteSupplierModal } from './DeleteSupplierModal'
import { SupplierFormModal } from './SupplierFormModal'
import { SupplierTable } from './SupplierTable'

export function SuppliersAdminSection() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [zones, setZones] = useState<Zone[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [formOpenFor, setFormOpenFor] = useState<Supplier | 'new' | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Supplier | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [supplierResult, zoneResult] = await Promise.all([
          listSuppliers(),
          listZones(),
        ])
        if (cancelled) return
        setSuppliers(supplierResult)
        setZones(zoneResult)
      } catch (err) {
        if (!cancelled)
          setError(
            err instanceof ApiError
              ? err.message
              : 'Could not load suppliers. Try again.',
          )
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [])

  async function handleSave(input: SupplierInput) {
    setSaving(true)
    try {
      if (formOpenFor && formOpenFor !== 'new') {
        const updated = await updateSupplier(formOpenFor.id, input)
        setSuppliers((prev) =>
          prev.map((s) => (s.id === updated.id ? updated : s)),
        )
      } else {
        const created = await createSupplier(input)
        setSuppliers((prev) => [...prev, created])
      }
      setFormOpenFor(null)
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Could not save supplier.',
      )
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
      setPendingDelete(null)
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Could not delete supplier.',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="space-y-4" aria-labelledby="suppliers-heading">
      <div className="flex items-center justify-between gap-4">
        <h2
          id="suppliers-heading"
          className="text-lg font-semibold text-gray-900"
        >
          Suppliers
        </h2>
        <button
          type="button"
          onClick={() => setFormOpenFor('new')}
          className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
        >
          + Add Supplier
        </button>
      </div>

      {error && (
        <p
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}

      {loading ? (
        <p className="text-sm text-gray-500">Loading suppliers...</p>
      ) : suppliers.length > 0 ? (
        <SupplierTable
          suppliers={suppliers}
          zones={zones}
          onEdit={setFormOpenFor}
          onDelete={setPendingDelete}
        />
      ) : (
        !error && (
          <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
            <p className="font-medium text-gray-900">No suppliers yet.</p>
          </div>
        )
      )}

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
    </section>
  )
}
