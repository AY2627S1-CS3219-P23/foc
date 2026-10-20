// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Request History — errands as requester or as courier, paged, per
// web/docs/wireframes/order-history.png: a table on wide screens, cards on
// mobile. Shows the credit effect for the viewer's side of each request.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: on order-service's real
// API (GET /requests/mine?role=&state=). A state filter (default Completed,
// the author's choice) replaces the "history" scope; a courier's list
// offers only the states a courier's request can end in. The backend has
// no completion or cancellation time, so each row shows when it was
// created (and an expiry its deadline).
// 2026-10-20: History holds every finished request — All finished by
// default, sent as one comma-separated state list — while active ones
// stay on My Requests (the author's decision).
// Author review: [pending]

import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import {
  StateFilter,
  type FilterOption,
} from '@/features/order/components/StateFilter'
import { StatusChip } from '@/features/order/components/StatusChip'
import {
  finishedStatuses,
  formatDateTime,
  statusLabels,
} from '@/features/order/status'
import type {
  RequestRole,
  RequestStatus,
  RequestSummary,
} from '@/features/order/types'
import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'

const PAGE_SIZE = 10

const tabs: { role: RequestRole; label: string }[] = [
  { role: 'requester', label: 'As Requester (My requests)' },
  { role: 'courier', label: 'As Courier (Deliveries)' },
]

const chip = (s: RequestStatus): FilterOption => ({
  key: s,
  label: statusLabels[s],
  states: [s],
})

// A courier is only on a request from acceptance on: theirs can't be
// cancelled (only an unaccepted request can) or rejected.
const filters: Record<RequestRole, FilterOption[]> = {
  requester: [
    { key: 'ALL', label: 'All finished', states: finishedStatuses },
    ...(['COMPLETED', 'CANCELLED', 'EXPIRED', 'REJECTED'] as const).map(chip),
  ],
  courier: [
    { key: 'ALL', label: 'All finished', states: ['COMPLETED', 'EXPIRED'] },
    ...(['COMPLETED', 'EXPIRED'] as const).map(chip),
  ],
}

function dateLine(r: RequestSummary): string {
  const created = `Created ${formatDateTime(r.createdAt)}`
  if (r.status === 'EXPIRED')
    return `${created} | Expired ${formatDateTime(r.deadline)}`
  return created
}

// What this request did to the viewer's balance, e.g. "+3" for a courier.
function creditEffect(r: RequestSummary, role: RequestRole): string {
  if (r.status !== 'COMPLETED') return `${r.creditAmount} credits`
  return role === 'courier'
    ? `+${r.creditAmount} credits`
    : `−${r.creditAmount} credits`
}

export function RequestHistory() {
  const [role, setRole] = useState<RequestRole>('requester')
  const [filterKey, setFilterKey] = useState('ALL')
  const filter =
    filters[role].find((f) => f.key === filterKey) ?? filters[role][0]
  const [page, setPage] = useState(0)
  const [requests, setRequests] = useState<RequestSummary[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    orderApi
      .listMyRequests({ role, state: filter.states, page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        setRequests(result.content)
        setTotal(result.totalElements)
        setTotalPages(result.totalPages)
        setError(null)
        // Defensive: if the page we asked for no longer exists, step back to
        // the last one that does instead of showing an empty, unpageable
        // list.
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
        }
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
  }, [role, filter, page])

  function selectRole(next: RequestRole) {
    if (next === role) return
    setLoading(true)
    setRole(next)
    // keep the chip if the other side offers it, else back to All finished
    if (!filters[next].some((f) => f.key === filterKey)) setFilterKey('ALL')
    setPage(0)
  }

  function selectFilter(next: FilterOption) {
    if (next.key === filterKey) return
    setLoading(true)
    setFilterKey(next.key)
    setPage(0)
  }

  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = page * PAGE_SIZE + requests.length

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

      <StateFilter
        label="Filter by state"
        options={filters[role]}
        value={filter.key}
        onChange={selectFilter}
      />

      {error && <Banner kind="error">{error}</Banner>}

      {loading ? (
        <p className="text-sm text-gray-500">Loading history…</p>
      ) : requests.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">Nothing here yet.</p>
          <p className="mt-1 text-sm text-gray-500">
            {filter.key === 'ALL'
              ? `Finished ${role === 'requester' ? 'requests' : 'deliveries'} will show up here.`
              : `${filter.label} ${role === 'requester' ? 'requests' : 'deliveries'} will show up here.`}
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
                  <th className="px-4 py-3 font-semibold">Item</th>
                  <th className="px-4 py-3 font-semibold">Pickup</th>
                  <th className="px-4 py-3 font-semibold">Drop-off</th>
                  <th className="px-4 py-3 font-semibold">Reward</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {requests.map((r) => (
                  <tr key={r.id}>
                    <td className="px-4 py-3 font-semibold text-gray-900">
                      #{r.id}
                    </td>
                    <td className="px-4 py-3">
                      <span className="block text-gray-900">{r.item}</span>
                      <span className="block text-xs text-gray-400">
                        {dateLine(r)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-700">
                      {r.supplierName}
                    </td>
                    <td className="px-4 py-3 text-gray-700">
                      {r.dropoffLocation}
                    </td>
                    <td className="px-4 py-3 font-semibold whitespace-nowrap text-gray-900">
                      {creditEffect(r, role)}
                    </td>
                    <td className="px-4 py-3">
                      <StatusChip status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile: cards */}
          <ul className="space-y-3 md:hidden">
            {requests.map((r) => (
              <li
                key={r.id}
                className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-gray-900">
                    Request #{r.id}
                  </span>
                  <StatusChip status={r.status} />
                </div>
                <p className="mt-1 font-medium text-gray-900">{r.item}</p>
                <p className="mt-2 text-sm text-gray-600">
                  <span className="font-semibold">Pick:</span> {r.supplierName}
                </p>
                <p className="text-sm text-gray-600">
                  <span className="font-semibold">Drop:</span>{' '}
                  {r.dropoffLocation}
                </p>
                <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-2 text-xs text-gray-400">
                  <span>{formatDateTime(r.createdAt)}</span>
                  <span className="text-sm font-semibold text-gray-900">
                    {creditEffect(r, role)}
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
