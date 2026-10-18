// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: My Requests — the requester's own errands with a progress tracker,
// per web/docs/wireframes/my-requests.png. Every action is enforced by
// order-service; the buttons only mirror what it will accept.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: on order-service's real
// API (GET /requests/mine?role=requester, POST /cancel, /complete). A state
// filter (one backend query per chip, default All) replaces the "current"
// scope; PENDING ("reserving credits") and REJECTED are shown; dispute and
// re-release are gone (no backend); steps answer a message, so the list
// reloads after one; the courier's name and the auto-complete time come
// from the request's detail and user-service.
// Author review: [pending]

import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { OrderProgress } from '@/features/order/components/OrderProgress'
import { StateFilter } from '@/features/order/components/StateFilter'
import { StatusChip } from '@/features/order/components/StatusChip'
import {
  allStatuses,
  autoCompleteAt,
  credits,
  deadlineLine,
  formatDateTime,
  isTerminal,
  statusLabels,
} from '@/features/order/status'
import type {
  RequestDetail,
  RequestStatus,
  RequestSummary,
} from '@/features/order/types'
import { useUsername } from '@/features/order/useUsername'
import { errorMessage } from '@/lib/api/http'
import { ConfirmModal } from '@/shared/components/ConfirmModal'
import { Pagination } from '@/shared/components/Pagination'

const PAGE_SIZE = 10
const FILTERS: (RequestStatus | undefined)[] = [undefined, ...allStatuses]

export function MyRequests() {
  const location = useLocation()
  const [state, setState] = useState<RequestStatus | undefined>(undefined)
  const [requests, setRequests] = useState<RequestSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null,
  )
  const [busyId, setBusyId] = useState<number | null>(null)
  const [pendingCancel, setPendingCancel] = useState<RequestSummary | null>(
    null,
  )
  const [refreshKey, setRefreshKey] = useState(0)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    let cancelled = false
    orderApi
      .listMyRequests({ role: 'requester', state, page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return
        setRequests(result.content)
        setTotalPages(result.totalPages)
        // an action can empty the last page (e.g. a cancel under a state
        // filter); step back instead of showing a blank page
        if (result.content.length === 0 && page > 0) setPage(page - 1)
      })
      .catch((err) => {
        if (!cancelled)
          setError(
            errorMessage(err, 'Could not load your requests. Try again.'),
          )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [state, page, refreshKey])

  function selectState(next: RequestStatus | undefined) {
    if (next === state) return
    setLoading(true)
    setState(next)
    setPage(0)
  }

  // A step answers only a message, so the list is reloaded to show the
  // request's new state — after a failure too, to show what it really is.
  async function run(
    request: RequestSummary,
    action: () => Promise<unknown>,
    success: string,
  ) {
    setBusyId(request.id)
    setError(null)
    try {
      await action()
      setNotice(success)
    } catch (err) {
      setNotice(null)
      setError(errorMessage(err, 'That did not go through. Try again.'))
    } finally {
      setBusyId(null)
      setRefreshKey((k) => k + 1)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">My Requests</h1>
          <p className="mt-1 text-sm text-gray-500">
            Track and manage your submitted errands on campus.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/history"
            className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            History
          </Link>
          <Link
            to="/my-requests/new"
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            + Create New Request
          </Link>
        </div>
      </div>

      <StateFilter
        label="Filter by state"
        options={FILTERS}
        value={state}
        onChange={selectState}
      />

      {notice && <Banner kind="success">{notice}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}

      {loading ? (
        <p className="text-sm text-gray-500">Loading your requests…</p>
      ) : requests.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            {state
              ? `No ${statusLabels[state].toLowerCase()} requests.`
              : 'You have no requests yet.'}
          </p>
          <p className="mt-1 text-sm text-gray-500">
            Need something collected on campus? Create a request.
          </p>
        </div>
      ) : (
        <ul className="space-y-4">
          {requests.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              busy={busyId === request.id}
              onCancel={() => setPendingCancel(request)}
              onConfirm={() =>
                run(
                  request,
                  () => orderApi.complete(request.id),
                  `Delivery confirmed — ${credits(request.creditAmount)} paid to your courier.`,
                )
              }
            />
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

      {pendingCancel && (
        <ConfirmModal
          title="Cancel this request?"
          confirmLabel="Cancel request"
          busyLabel="Cancelling…"
          busy={busyId === pendingCancel.id}
          danger
          onCancel={() => setPendingCancel(null)}
          onConfirm={async () => {
            const request = pendingCancel
            await run(
              request,
              () => orderApi.cancel(request.id),
              `Request #${request.id} cancelled — ${credits(request.creditAmount)} released back to your balance.`,
            )
            setPendingCancel(null)
          }}
        >
          <p className="text-sm text-gray-600">
            Request #{pendingCancel.id} will be cancelled and the{' '}
            {credits(pendingCancel.creditAmount)} reserved for it will be
            refunded.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}

interface RequestCardProps {
  request: RequestSummary
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
}

const IN_PROGRESS: RequestStatus[] = ['ACCEPTED', 'COLLECTED', 'ARRIVED']

function RequestCard({ request, busy, onCancel, onConfirm }: RequestCardProps) {
  const dimmed =
    request.status === 'CANCELLED' ||
    request.status === 'EXPIRED' ||
    request.status === 'REJECTED'
  const outlineButton =
    'rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50'
  const detail = useDetail(request.id, IN_PROGRESS.includes(request.status))
  const courier = useUsername(detail?.courierId ?? null)
  const autoAt = detail ? autoCompleteAt(detail) : null

  return (
    <li
      className={`rounded-lg border border-gray-200 bg-white p-5 shadow-sm ${dimmed ? 'opacity-70' : ''}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="font-semibold text-gray-900">Request #{request.id}</h2>
          <StatusChip status={request.status} />
        </div>
        <span className="font-semibold whitespace-nowrap text-gray-900">
          {credits(request.creditAmount)}
        </span>
      </div>

      <dl className="mt-4 grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
        <Field label="Item" value={request.item} strong />
        <Field label="Deadline" value={deadlineLine(request)} />
        <Field label="Pickup from" value={request.supplierName} />
        <Field label="Drop off to" value={request.dropoffLocation} />
      </dl>

      {courier && (
        <p className="mt-4 text-sm text-gray-600">
          Assigned courier:{' '}
          <span className="font-semibold text-gray-900">{courier}</span>
        </p>
      )}

      <div className="mt-4 space-y-3 border-t border-gray-100 pt-4">
        {!isTerminal(request.status) && request.status !== 'PENDING' && (
          <OrderProgress status={request.status} />
        )}

        {request.status === 'PENDING' && (
          <p className="text-sm text-gray-500">
            Reserving your credits — couriers will see this request once they
            are held.
          </p>
        )}

        {request.status === 'CREATED' && (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-gray-500">
              Waiting for a courier to accept.
            </p>
            <button
              type="button"
              onClick={onCancel}
              disabled={busy}
              className={outlineButton}
            >
              Cancel request
            </button>
          </div>
        )}

        {request.status === 'ARRIVED' && (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-gray-500">
              Your courier has arrived at the drop-off.
              {autoAt &&
                ` Auto-completes at ${formatDateTime(autoAt.toISOString())} if you don’t respond.`}
            </p>
            <button
              type="button"
              onClick={onConfirm}
              disabled={busy}
              className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {busy ? 'Working…' : 'Confirm delivery'}
            </button>
          </div>
        )}

        {request.status === 'REJECTED' && (
          <p className="text-sm font-medium text-red-700">
            ✕ REQUEST REJECTED — NOT ENOUGH AVAILABLE CREDITS
          </p>
        )}

        {(request.status === 'CANCELLED' || request.status === 'EXPIRED') && (
          <p className="text-sm font-medium text-gray-500">
            {request.status === 'CANCELLED'
              ? '✕ REQUEST CANCELLED — CREDITS REFUNDED'
              : '⚠ REQUEST EXPIRED — CREDITS REFUNDED'}
          </p>
        )}
      </div>
    </li>
  )
}

// The list items carry no courier or arrival time, so an in-progress card
// reads its request's detail once.
function useDetail(requestId: number, enabled: boolean): RequestDetail | null {
  const [detail, setDetail] = useState<RequestDetail | null>(null)
  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    orderApi.getRequest(requestId).then(
      (d) => {
        if (!cancelled) setDetail(d)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [requestId, enabled])
  return enabled ? detail : null
}

function Field({
  label,
  value,
  strong = false,
}: {
  label: string
  value: string
  strong?: boolean
}) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
        {label}
      </dt>
      <dd className={strong ? 'font-medium text-gray-900' : 'text-gray-700'}>
        {value}
      </dd>
    </div>
  )
}
