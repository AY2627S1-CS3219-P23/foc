// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: the Admin Dashboard's Suppliers section, per
// web/docs/wireframes/admin-dashboard.png — name, categories, location
// and hours, as a table at md+ and cards below, paged with the shared
// Pagination. The wireframe's Zone column is left out (zones were
// dropped, docs/supplier-service.md D4).
// 2026-09-30: Add / Edit / Delete now that supplier-service's CRUD
// endpoints (#104, PR #153) have merged (team decision: list first, CRUD
// once the endpoints exist). Reuses the Suppliers page's
// SupplierFormModal and DeleteSupplierModal and the same api.ts calls;
// success is announced in a status line, failures in the alert. A failed
// load now offers "Try again" instead of leaving only the error (#154).
// PR #156 review: a failed save shows its reason inside the form (the
// form stays open over the section); a failed delete closes the confirm
// so the section's alert is visible; "Try again" stays whenever the load
// failed; the rows kept after a failed load are only reused for the same
// page, with actions off until it reloads; the success line clears on a
// page change and after 4s, like the Suppliers page. Success and error
// texts reworded to match the Suppliers page's.
// PR #156 re-review: those texts and the auto-dismiss now come from
// ../messages and ../useAutoDismissed, shared with the Suppliers page; a
// failed delete names the supplier (the confirm is gone by then); a
// deleted row leaves the kept rows at once, so a failed reload after it
// can't show it again.
// Supplier-domain UI: flag changes to its owners.
// Author review: Ryan to review via the PR.

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'
import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  updateSupplier,
} from '../api'
import {
  createdSupplier,
  deletedSupplier,
  deleteSupplierFailed,
  SAVE_SUPPLIER_FAILED,
  updatedSupplier,
} from '../messages'
import type { PagedResponse, Supplier, SupplierInput } from '../types'
import { useAutoDismissed } from '../useAutoDismissed'
import { DeleteSupplierModal } from './DeleteSupplierModal'
import { SupplierFormModal } from './SupplierFormModal'

export const ADMIN_SUPPLIERS_PAGE_SIZE = 20

// Identifies one list request, so a newer one shows as loading until its
// own result arrives.
function queryKeyOf(page: number, reloadCount: number) {
  return `${page}:${reloadCount}`
}

interface LoadResult {
  key: string
  data: PagedResponse<Supplier> | null
  error: string | null
}

function hours(supplier: Supplier) {
  return `${supplier.openingTime}–${supplier.closingTime}`
}

function withoutSupplier(data: PagedResponse<Supplier>, id: string) {
  return { ...data, content: data.content.filter((s) => s.id !== id) }
}

const linkClass =
  'text-sm font-medium text-gray-900 hover:underline disabled:cursor-not-allowed disabled:opacity-50'
const deleteClass =
  'text-sm font-medium text-red-600 hover:underline disabled:cursor-not-allowed disabled:opacity-50'

export function SuppliersAdminSection() {
  const [page, setPage] = useState(1) // 1-based; the API is 0-based
  // bumped to reload the current page after a change or a failed load
  const [reloadCount, setReloadCount] = useState(0)
  const [result, setResult] = useState<LoadResult | null>(null)
  // the last page that loaded, kept on screen when a reload of that same
  // page fails
  const [lastData, setLastData] = useState<{
    page: number
    data: PagedResponse<Supplier>
  } | null>(null)
  const [formOpenFor, setFormOpenFor] = useState<Supplier | 'new' | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Supplier | null>(null)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  // a failed save, shown inside the form, which stays open over the section
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const key = queryKeyOf(page, reloadCount)
    listSuppliers({ page: page - 1, size: ADMIN_SUPPLIERS_PAGE_SIZE }).then(
      (data) => {
        if (cancelled) return
        // past the last page (a delete emptied it): go to the last page
        const lastPage = Math.max(1, data.totalPages)
        if (page > lastPage) {
          setPage(lastPage)
          return
        }
        setResult({ key, data, error: null })
        setLastData({ page, data })
      },
      (err: unknown) => {
        if (!cancelled)
          setResult({
            key,
            data: null,
            error: errorMessage(err, 'Could not load suppliers.'),
          })
      },
    )
    return () => {
      cancelled = true
    }
  }, [page, reloadCount])

  // a newer request shows as loading until its own result arrives; the
  // previous rows stay on screen meanwhile, and after a failed reload of
  // the same page (never under another page's number)
  const loading = result?.key !== queryKeyOf(page, reloadCount)
  const data = result?.data ?? (lastData?.page === page ? lastData.data : null)
  const loadError = loading ? null : (result?.error ?? null)

  useAutoDismissed(success, setSuccess)

  function handlePageChange(value: number) {
    setPage(value)
    setActionError(null)
    setSuccess(null)
  }

  function reload() {
    setReloadCount((n) => n + 1)
  }

  function retryLoad() {
    setActionError(null)
    reload()
  }

  function openForm(target: Supplier | 'new') {
    setFormError(null)
    setFormOpenFor(target)
  }

  async function handleSave(input: SupplierInput) {
    const editing = formOpenFor !== 'new' ? formOpenFor : null
    setSaving(true)
    setActionError(null)
    setFormError(null)
    setSuccess(null)
    try {
      if (editing) {
        await updateSupplier(editing.id, input)
      } else {
        await createSupplier(input)
      }
      setFormOpenFor(null)
      setSuccess(
        editing ? updatedSupplier(input.name) : createdSupplier(input.name),
      )
      reload()
    } catch (err) {
      setFormError(errorMessage(err, SAVE_SUPPLIER_FAILED))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return
    const target = pendingDelete
    setSaving(true)
    setActionError(null)
    setSuccess(null)
    try {
      await deleteSupplier(target.id)
      setSuccess(deletedSupplier(target.name))
      // known gone, not just outdated: drop it from the rows kept on
      // screen, so a failed reload doesn't bring it back
      setResult((r) =>
        r?.data ? { ...r, data: withoutSupplier(r.data, target.id) } : r,
      )
      setLastData(
        (l) => l && { ...l, data: withoutSupplier(l.data, target.id) },
      )
      // the page's only row, on a later page: go straight to the previous
      // page instead of fetching the emptied one. `data` is current:
      // actions are disabled while loading and after a failed load
      if (page > 1 && data?.content.length === 1) {
        setPage(page - 1)
      } else {
        reload()
      }
    } catch (err) {
      setActionError(errorMessage(err, deleteSupplierFailed(target.name)))
    } finally {
      // closed either way: the confirm would cover the section's alert
      setPendingDelete(null)
      setSaving(false)
    }
  }

  // off while the rows may be outdated: loading, or kept after a failed load
  const actionsDisabled = loading || saving || loadError !== null

  function rowActions(supplier: Supplier) {
    return (
      <div className="flex gap-3">
        <button
          type="button"
          disabled={actionsDisabled}
          onClick={() => openForm(supplier)}
          className={linkClass}
          aria-label={`Edit ${supplier.name}`}
        >
          Edit
        </button>
        <button
          type="button"
          disabled={actionsDisabled}
          onClick={() => setPendingDelete(supplier)}
          className={deleteClass}
          aria-label={`Delete ${supplier.name}`}
        >
          Delete
        </button>
      </div>
    )
  }

  return (
    <section className="space-y-4" aria-labelledby="suppliers-heading">
      <div className="flex items-center justify-between gap-3">
        <h2
          id="suppliers-heading"
          className="text-lg font-semibold text-gray-900"
        >
          Suppliers
        </h2>
        <button
          type="button"
          onClick={() => openForm('new')}
          disabled={saving}
          className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          + Add Supplier
        </button>
      </div>

      {success && (
        <p
          role="status"
          className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          {success}
        </p>
      )}

      {actionError && (
        <p
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {actionError}
        </p>
      )}

      {loadError && (
        <div
          role="alert"
          className="flex items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          <span>{loadError}</span>
          <button
            type="button"
            onClick={retryLoad}
            className="shrink-0 font-medium underline"
          >
            Try again
          </button>
        </div>
      )}

      {!data ? (
        loading && <p className="text-sm text-gray-500">Loading suppliers...</p>
      ) : data.content.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">No suppliers yet.</p>
        </div>
      ) : (
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
                {data.content.map((supplier) => (
                  <tr key={supplier.id}>
                    <td className="px-4 py-3 font-medium text-gray-900">
                      {supplier.name}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {supplier.categories.join(', ')}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {supplier.location}
                    </td>
                    <td className="px-4 py-3 tabular-nums text-gray-600">
                      {hours(supplier)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end">
                        {rowActions(supplier)}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile */}
          <ul className="space-y-3 md:hidden">
            {data.content.map((supplier) => (
              <li
                key={supplier.id}
                className="rounded-lg border border-gray-200 bg-white p-4"
              >
                <p className="font-medium text-gray-900">{supplier.name}</p>
                <p className="text-sm text-gray-600">
                  {supplier.location} · {hours(supplier)}
                </p>
                <p className="text-xs text-gray-400">
                  {supplier.categories.join(' · ')}
                </p>
                <div className="mt-3">{rowActions(supplier)}</div>
              </li>
            ))}
          </ul>

          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={handlePageChange}
            disabled={loading}
          />
        </>
      )}

      {formOpenFor && (
        <SupplierFormModal
          initial={formOpenFor === 'new' ? undefined : formOpenFor}
          onCancel={() => setFormOpenFor(null)}
          onSave={handleSave}
          saving={saving}
          error={formError}
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
