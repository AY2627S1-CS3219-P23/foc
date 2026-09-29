// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: the Admin Dashboard's Suppliers section, per
// web/docs/wireframes/admin-dashboard.png — a read-only list for now
// (team decision): name, categories, location and hours, as a table at
// md+ and cards below, paged with the shared Pagination. The wireframe's
// Zone column is left out (zones were dropped, docs/supplier-service.md
// D4); Add/Edit/Delete wait on supplier-service's CRUD endpoints (#104).
// Supplier-domain UI: flag changes to its owners.
// Author review: Ryan to review via the PR.

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'
import { listSuppliers } from '../api'
import type { PagedResponse, Supplier } from '../types'

export const ADMIN_SUPPLIERS_PAGE_SIZE = 20

interface LoadResult {
  page: number
  data: PagedResponse<Supplier> | null
  error: string | null
}

function hours(supplier: Supplier) {
  return `${supplier.openingTime}–${supplier.closingTime}`
}

export function SuppliersAdminSection() {
  const [page, setPage] = useState(1) // 1-based; the API is 0-based
  const [result, setResult] = useState<LoadResult | null>(null)

  useEffect(() => {
    let cancelled = false
    listSuppliers({ page: page - 1, size: ADMIN_SUPPLIERS_PAGE_SIZE }).then(
      (data) => {
        if (!cancelled) setResult({ page, data, error: null })
      },
      (err: unknown) => {
        if (!cancelled)
          setResult({
            page,
            data: null,
            error: errorMessage(err, 'Could not load suppliers. Try again.'),
          })
      },
    )
    return () => {
      cancelled = true
    }
  }, [page])

  // a newer page shows as loading until its own result arrives; the
  // previous rows stay on screen meanwhile
  const loading = result?.page !== page
  const data = result?.data ?? null
  const error = loading ? null : (result?.error ?? null)

  return (
    <section className="space-y-4" aria-labelledby="suppliers-heading">
      <h2
        id="suppliers-heading"
        className="text-lg font-semibold text-gray-900"
      >
        Suppliers
      </h2>

      {error && (
        <p
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
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
              </li>
            ))}
          </ul>

          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={setPage}
            disabled={loading}
          />
        </>
      )}
    </section>
  )
}
