// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Requests ("Available Errands") — the courier's feed of open
// requests with Accept, per web/docs/wireframes/open-requests.png, plus a
// strip of the courier's own active deliveries. When two couriers race,
// the loser gets the service's conflict message and the feed refreshes.
// The wireframe's "Too far (>1 km)" state is not built — no distance rule
// has been decided.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: on order-service's real
// API — GET /requests?sort=deadline (open, deadline still ahead, nearest
// first), POST /accept {requestId}, and the strip from
// /requests/mine?role=courier, one call per in-progress state. List items
// carry no requester, so the "Your request" button and "Requested by" line
// are gone; order-service refuses a self-accept with a 409, shown as-is.
// Author review: [pending]

import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { StatusChip } from '@/features/order/components/StatusChip'
import { credits, formatDateTime, relativeTime } from '@/features/order/status'
import type { RequestStatus, RequestSummary } from '@/features/order/types'
import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'

const PAGE_SIZE = 9
const ACTIVE: RequestStatus[] = ['ACCEPTED', 'COLLECTED', 'ARRIVED']

// The courier's in-progress deliveries, newest first. A failure here is
// secondary: the feed still loads and the strip just stays empty.
function loadActiveDeliveries(): Promise<RequestSummary[]> {
  return Promise.all(
    ACTIVE.map((state) =>
      orderApi
        .listMyRequests({ role: 'courier', state, page: 0, size: 20 })
        .then((page) => page.content),
    ),
  )
    .then((pages) =>
      pages.flat().sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    )
    .catch(() => [])
}

export function OpenRequests() {
  const navigate = useNavigate()
  const [requests, setRequests] = useState<RequestSummary[]>([])
  const [deliveries, setDeliveries] = useState<RequestSummary[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  // a failed load clears on the next successful one; an accept failure
  // (e.g. "already taken") must survive the refetch it triggers, so the
  // two are kept apart
  const [loadError, setLoadError] = useState<string | null>(null)
  const [acceptError, setAcceptError] = useState<string | null>(null)
  const [acceptingId, setAcceptingId] = useState<number | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      orderApi.listOpenRequests({ page, size: PAGE_SIZE, sort: 'deadline' }),
      loadActiveDeliveries(),
    ])
      .then(([open, mine]) => {
        if (cancelled) return
        setRequests(open.content)
        setTotalPages(open.totalPages)
        setDeliveries(mine)
        setLoadError(null)
        // The feed can shrink under us (requests accepted or expired
        // since we last loaded): step back to the last page that still
        // exists instead of staring at an empty, unpageable one. The
        // page change re-runs this effect.
        if (page > 0 && page >= open.totalPages) {
          setPage(Math.max(0, open.totalPages - 1))
        }
      })
      .catch((err) => {
        if (!cancelled)
          setLoadError(errorMessage(err, 'Could not load requests. Try again.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [page, refreshKey])

  const accept = useCallback(
    async (request: RequestSummary) => {
      setAcceptingId(request.id)
      setAcceptError(null)
      try {
        await orderApi.accept(request.id)
        navigate(`/deliveries/${request.id}`)
      } catch (err) {
        // 409: another courier got there first, it is your own request, or
        // its deadline has passed; 404: it is gone
        setAcceptError(errorMessage(err, 'Could not accept this request.'))
        setAcceptingId(null)
        setRefreshKey((k) => k + 1)
      }
    },
    [navigate],
  )

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">
            Available Errands
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Help peers on campus and earn reward credits.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setRefreshKey((k) => k + 1)}
          className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          Refresh
        </button>
      </div>

      {loadError && <Banner kind="error">{loadError}</Banner>}
      {acceptError && <Banner kind="error">{acceptError}</Banner>}

      {deliveries.length > 0 && (
        <section aria-label="Your active deliveries" className="space-y-2">
          <h2 className="text-sm font-semibold text-gray-900">
            Your active deliveries
          </h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {deliveries.map((d) => (
              <li key={d.id}>
                <Link
                  to={`/deliveries/${d.id}`}
                  className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm shadow-sm hover:bg-gray-50"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-gray-900">
                      #{d.id} · {d.item}
                    </span>
                    <span className="block truncate text-gray-500">
                      {d.supplierName} → {d.dropoffLocation}
                    </span>
                  </span>
                  <StatusChip status={d.status} />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {loading ? (
        <p className="text-sm text-gray-500">Loading requests…</p>
      ) : requests.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            No open errands right now.
          </p>
          <p className="mt-1 text-sm text-gray-500">
            Check back soon, or refresh.
          </p>
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {requests.map((request) => (
            <li
              key={request.id}
              className="flex flex-col rounded-lg border border-gray-200 bg-white p-5 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <p className="text-xs font-medium tracking-wide text-gray-400 uppercase">
                  {request.supplierName} → {request.dropoffLocation}
                </p>
                <span className="font-semibold whitespace-nowrap text-gray-900">
                  {credits(request.creditAmount)}
                </span>
              </div>
              <h2 className="mt-2 font-semibold text-gray-900">
                {request.item}
              </h2>

              <dl className="mt-3 space-y-2 border-t border-gray-100 pt-3 text-sm">
                <div>
                  <dt className="text-xs tracking-wide text-gray-400 uppercase">
                    Pickup
                  </dt>
                  <dd className="text-gray-800">{request.supplierName}</dd>
                </div>
                <div>
                  <dt className="text-xs tracking-wide text-gray-400 uppercase">
                    Drop off
                  </dt>
                  <dd className="text-gray-800">{request.dropoffLocation}</dd>
                </div>
              </dl>

              <p className="mt-3 mb-4 text-xs text-gray-400">
                Deadline: {formatDateTime(request.deadline)} (
                {relativeTime(request.deadline)})
              </p>

              <button
                type="button"
                onClick={() => accept(request)}
                disabled={acceptingId !== null}
                className="mt-auto rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-500"
              >
                {acceptingId === request.id ? 'Accepting…' : 'Accept'}
              </button>
            </li>
          ))}
        </ul>
      )}

      {!loading && totalPages > 1 && (
        <div className="flex justify-end">
          <Pagination
            page={page + 1}
            totalPages={totalPages}
            onPageChange={(p) => setPage(p - 1)}
          />
        </div>
      )}
    </div>
  )
}
