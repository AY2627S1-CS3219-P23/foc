// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Request History — finished (completed / cancelled / expired)
// errands, as requester or as courier, paged, per
// web/docs/wireframes/order-history.png: a table on wide screens, cards on
// mobile. Shows the credit effect for the viewer's side of each order.
// Author review: [pending]

import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { StatusChip } from '@/features/order/components/StatusChip'
import { formatDateTime } from '@/features/order/status'
import type { Order, OrderRole } from '@/features/order/types'
import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'

const PAGE_SIZE = 10

const tabs: { role: OrderRole; label: string }[] = [
  { role: 'requester', label: 'As Requester (My requests)' },
  { role: 'courier', label: 'As Courier (Deliveries)' },
]

function finishedLine(o: Order): string {
  const created = `Created ${formatDateTime(o.createdAt)}`
  if (o.status === 'COMPLETED')
    return `${created} | Completed ${formatDateTime(o.updatedAt)}`
  if (o.status === 'CANCELLED')
    return `${created} | Cancelled ${formatDateTime(o.updatedAt)}`
  return `${created} | Expired ${formatDateTime(o.deadline)}`
}

// What this order did to the viewer's balance, e.g. "+3" for a courier.
function creditEffect(o: Order, role: OrderRole): string {
  if (o.status !== 'COMPLETED') return `${o.reward} credits`
  return role === 'courier' ? `+${o.reward} credits` : `−${o.reward} credits`
}

export function RequestHistory() {
  const [role, setRole] = useState<OrderRole>('requester')
  const [page, setPage] = useState(0)
  const [orders, setOrders] = useState<Order[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    orderApi
      .listMyOrders({ as: role, scope: 'history', page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        setOrders(result.content)
        setTotal(result.totalElements)
        setTotalPages(result.totalPages)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load your history. Try again.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [role, page])

  function selectRole(next: OrderRole) {
    if (next === role) return
    setLoading(true)
    setRole(next)
    setPage(0)
  }

  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = page * PAGE_SIZE + orders.length

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-semibold text-gray-900">
          Request History
        </h1>
        <Link
          to="/my-requests"
          className="text-sm text-gray-500 hover:text-gray-900"
        >
          ← My Requests
        </Link>
      </div>

      <div
        role="tablist"
        aria-label="History view"
        className="flex rounded-md border border-gray-200 bg-white p-1 sm:inline-flex"
      >
        {tabs.map((tab) => (
          <button
            key={tab.role}
            role="tab"
            type="button"
            aria-selected={role === tab.role}
            onClick={() => selectRole(tab.role)}
            className={`flex-1 rounded px-3 py-1.5 text-sm font-medium sm:flex-none ${
              role === tab.role
                ? 'bg-gray-900 text-white'
                : 'text-gray-600 hover:bg-gray-50'
            }`}
          >
            <span className="sm:hidden">
              {tab.role === 'requester' ? 'As Requester' : 'As Courier'}
            </span>
            <span className="hidden sm:inline">{tab.label}</span>
          </button>
        ))}
      </div>

      {error && <Banner kind="error">{error}</Banner>}

      {loading ? (
        <p className="text-sm text-gray-500">Loading history…</p>
      ) : orders.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">Nothing here yet.</p>
          <p className="mt-1 text-sm text-gray-500">
            Finished {role === 'requester' ? 'requests' : 'deliveries'} will
            show up here.
          </p>
        </div>
      ) : (
        <>
          {/* Wide screens: table */}
          <div className="hidden overflow-x-auto rounded-lg border border-gray-200 bg-white shadow-sm md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 text-xs tracking-wide text-gray-500 uppercase">
                <tr>
                  <th className="px-4 py-3 font-semibold">Request ID</th>
                  <th className="px-4 py-3 font-semibold">Description</th>
                  <th className="px-4 py-3 font-semibold">Pickup</th>
                  <th className="px-4 py-3 font-semibold">Drop-off</th>
                  <th className="px-4 py-3 font-semibold">Reward</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => (
                  <tr key={o.id}>
                    <td className="px-4 py-3 font-semibold text-gray-900">
                      #{o.id}
                    </td>
                    <td className="px-4 py-3">
                      <span className="block text-gray-900">
                        {o.description}
                      </span>
                      <span className="block text-xs text-gray-400">
                        {finishedLine(o)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-700">
                      {o.supplierName}
                    </td>
                    <td className="px-4 py-3 text-gray-700">
                      {o.dropoffLocation}
                    </td>
                    <td className="px-4 py-3 font-semibold whitespace-nowrap text-gray-900">
                      {creditEffect(o, role)}
                    </td>
                    <td className="px-4 py-3">
                      <StatusChip status={o.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile: cards */}
          <ul className="space-y-3 md:hidden">
            {orders.map((o) => (
              <li
                key={o.id}
                className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-gray-900">
                    Request #{o.id}
                  </span>
                  <StatusChip status={o.status} />
                </div>
                <p className="mt-1 font-medium text-gray-900">
                  {o.description}
                </p>
                <p className="mt-2 text-sm text-gray-600">
                  <span className="font-semibold">Pick:</span> {o.supplierName}
                </p>
                <p className="text-sm text-gray-600">
                  <span className="font-semibold">Drop:</span>{' '}
                  {o.dropoffLocation}
                </p>
                <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-2 text-xs text-gray-400">
                  <span>{formatDateTime(o.updatedAt)}</span>
                  <span className="text-sm font-semibold text-gray-900">
                    {creditEffect(o, role)}
                  </span>
                </div>
              </li>
            ))}
          </ul>

          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
            <span>
              Showing {from}-{to} of {total}{' '}
              {total === 1 ? 'request' : 'requests'}
            </span>
            {totalPages > 1 && (
              <Pagination
                page={page + 1}
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)}
              />
            )}
          </div>
        </>
      )}
    </div>
  )
}
