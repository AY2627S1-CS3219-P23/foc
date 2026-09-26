// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: Suppliers page (browse/search/filter + admin CRUD), per
// web/docs/wireframes/suppliers.png and add-edit-supplier.png.
// Implements F1.2, F1.2.1, F2.1, F2.2, F2.2.1, F1.1-F1.1.4.
//
// TEMPORARY: admin gating is a hardcoded constant below because
// src/shared/auth/ doesn't exist yet (see its README). Replace
// IS_ADMIN with the real role check once User Service auth lands —
// search this file for "TEMPORARY" when that happens.
//
// 2026-09-26 (issue #133): wired to the real, now-paginated
// GET /suppliers. Removed the `listZones()` call — the backend has no
// /zones endpoint (team dropped the zone feature) and calling it was
// throwing inside Promise.all, breaking this page's initial load
// entirely. Added page state + Prev/Next controls for the D2
// "paginating through supplier data" UI requirement; page resets to 0
// whenever the search/category filters change.
// 2026-09-26: removed the zone plumbing entirely (state, props passed
// to SupplierFilterBar/SupplierFormModal/SupplierDetailPanel) — the
// empty, required Campus Zone <select> in the edit form could never
// pass validation with no zones to populate it, blocking every edit.
// 2026-09-26: fetch the category-filter options from the new
// GET /suppliers/categories endpoint instead of deriving them from the
// current (filtered, paginated) `suppliers` state — the derived list
// was a bug: filtering to "Food" then reopening the dropdown only
// offered categories present on Food suppliers, not every category.
// Reviewed by: [pending]

import { Fragment, useEffect, useState } from 'react'

import {
  createSupplier,
  deleteSupplier,
  listCategories,
  listSuppliers,
  updateSupplier,
} from '@/features/supplier/api'
import { DeleteSupplierModal } from '@/features/supplier/components/DeleteSupplierModal'
import { SupplierCard } from '@/features/supplier/components/SupplierCard'
import { SupplierDetailPanel } from '@/features/supplier/components/SupplierDetailPanel'
import { SupplierFilterBar } from '@/features/supplier/components/SupplierFilterBar'
import { SupplierFormModal } from '@/features/supplier/components/SupplierFormModal'
import type { Supplier, SupplierInput } from '@/features/supplier/types'
import { ApiError } from '@/lib/api/http'

// TEMPORARY — see file header.
const IS_ADMIN = true

const PAGE_SIZE = 10

export function Suppliers() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [formOpenFor, setFormOpenFor] = useState<Supplier | 'new' | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Supplier | null>(null)
  const [saving, setSaving] = useState(false)

  // Filter changes should always jump back to the first page — a
  // stale page number from a previous, larger result set would
  // otherwise silently show an empty page. Reset during render (React's
  // recommended pattern for "adjust state when a prop changes") rather
  // than in an effect, which would cause an extra render pass.
  const [prevFilters, setPrevFilters] = useState({ query, category })
  if (query !== prevFilters.query || category !== prevFilters.category) {
    setPrevFilters({ query, category })
    setPage(0)
  }

  useEffect(() => {
    let cancelled = false

    async function loadSuppliers() {
      setLoading(true)
      setError(null)
      try {
        const result = await listSuppliers({ search: query, category, page, size: PAGE_SIZE })
        if (cancelled) return
        setSuppliers(result.content)
        setTotalPages(result.totalPages)
        setTotalElements(result.totalElements)
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
  }, [query, category, page])

  // Fetched once, independent of the current search/category filter —
  // this must always offer every category that exists, not just those
  // present on the currently filtered/paginated suppliers.
  useEffect(() => {
    let cancelled = false

    listCategories()
      .then((result) => {
        if (!cancelled) setCategories(result)
      })
      .catch(() => {
        // Non-fatal: the filter dropdown just falls back to "All" only.
      })

    return () => {
      cancelled = true
    }
  }, [])

  const selected = suppliers.find((s) => s.id === selectedId) ?? null

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
                Try adjusting your search query or category filter.
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

          {!loading && totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded-md border border-gray-200 px-3 py-1.5 font-medium hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent"
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {totalPages} · {totalElements} suppliers
              </span>
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="rounded-md border border-gray-200 px-3 py-1.5 font-medium hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent"
              >
                Next
              </button>
            </div>
          )}
        </div>

        {/* Desktop: side panel column. */}
        {selected && (
          <div className="hidden lg:block">
            <SupplierDetailPanel
              supplier={selected}
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
