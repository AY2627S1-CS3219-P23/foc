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
// 2026-10-19: the filter offers only All, Open, Accepted, Collected,
// Arrived and Completed (the author's choice).
// 2026-10-20: only active requests live here (All active by default,
// sent as one comma-separated state list); completed, cancelled,
// expired and rejected ones are in History (the author's decision).
// 2026-10-20: while a request is PENDING the list is refetched every 2
// seconds (the author's request), until credit reservation settles it.
// Author review: [pending]
// 2026-10-27, Claude Code (Fable 5), issue #233: a pushed request event
// for a listed id bumps refreshKey (useRequestRefetch), and an expanded
// card's detail fetch re-runs with it so the courier and arrival time
// it shows refresh with the list.
// 2026-10-28, Claude Code (Opus 5), PR #264 review (@Sinnez1): the
// detail fetches are keyed per request instead of off refreshKey, so
// the 2 s PENDING poll (and an action on another card) no longer sends
// a GET /requests/{id} for every expanded card every two seconds —
// only the rows a push actually touched, plus the one acted on, re-read
// their detail. The list fetch also clears its error on success, so a
// background refetch that failed once stops showing a stale banner.
// 2026-10-29, Claude Code (Opus 5.5), issue #241 (team decisions,
// docs/disputes.md): "Report a problem" (DisputeModal) on an ARRIVED
// request before its autoCompleteAt and on a COLLECTED one past its
// deadline, both read from the list item; "Confirm delivery" asks first
// in a ConfirmModal that states the payment, says it can't be undone and
// links to "Report a problem"; an ARRIVED card counts down to
// order-service's autoCompleteAt on a ticking clock, and once it has
// passed says "Completing automatically…" until the sweep runs; DISPUTED
// requests stay listed (with a chip) until an admin resolves them.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'

import { useRequestRefetch } from '@/features/notifications/useRequestRefetch'
import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { DisputeModal } from '@/features/order/components/DisputeModal'
import { OrderProgress } from '@/features/order/components/OrderProgress'
import {
  StateFilter,
  type FilterOption,
} from '@/features/order/components/StateFilter'
import { StatusChip } from '@/features/order/components/StatusChip'
import {
  activeStatuses,
  autoCompleteAt,
  canReportProblem,
  credits,
  deadlineLine,
  disputeCategoryLabels,
  formatDateTime,
  isTerminal,
  relativeTime,
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
// How often the list is refetched while a request is still PENDING.
const PENDING_POLL_MS = 2000
// How often the countdown and the "Report a problem" button are re-checked.
const CLOCK_TICK_MS = 1000
// Active requests only (the author's choice): "All active" also covers
// PENDING, which has no chip of its own. Finished ones are in History.
const ALL_ACTIVE: FilterOption = {
  key: 'ALL',
  label: 'All active',
  states: activeStatuses,
}
const FILTERS: FilterOption[] = [
  ALL_ACTIVE,
  ...(
    [
      'CREATED',
      'ACCEPTED',
      'COLLECTED',
      'ARRIVED',
      'DISPUTED',
    ] as RequestStatus[]
  ).map((s) => ({ key: s, label: statusLabels[s], states: [s] })),
]

// The current time, re-read every second while `enabled`, so a countdown
// moves and a button disappears once its window closes.
function useNow(enabled: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const timer = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS)
    return () => clearInterval(timer)
  }, [enabled])
  return now
}

export function MyRequests() {
  const location = useLocation()
  const [filter, setFilter] = useState<FilterOption>(ALL_ACTIVE)
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
  const [pendingConfirm, setPendingConfirm] = useState<RequestSummary | null>(
    null,
  )
  const [reporting, setReporting] = useState<RequestSummary | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  // Per-request detail versions: bumping one re-reads just that card's
  // GET /requests/{id}. Keyed by id rather than shared with refreshKey,
  // which every list refetch bumps — including the 2 s PENDING poll
  // (PR #264 review).
  const [detailVersions, setDetailVersions] = useState<Record<number, number>>(
    {},
  )
  const bumpDetails = (ids: number[]) =>
    setDetailVersions((prev) => {
      const next = { ...prev }
      for (const id of ids) next[id] = (next[id] ?? 0) + 1
      return next
    })
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  // only ARRIVED and COLLECTED cards change with the time
  const now = useNow(
    requests.some((r) => r.status === 'ARRIVED' || r.status === 'COLLECTED'),
  )

  useEffect(() => {
    let cancelled = false
    orderApi
      .listMyRequests({
        role: 'requester',
        state: filter.states,
        page,
        size: PAGE_SIZE,
      })
      .then((result) => {
        if (cancelled) return
        // cleared here, not only on a user action: a background refetch
        // (push or PENDING poll) that failed once must not leave the
        // banner up once the next one succeeds (PR #264 review)
        setError(null)
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
  }, [filter, page, refreshKey])

  // A PENDING request becomes CREATED or REJECTED once credit-service
  // answers, so keep refetching while one is shown. Each refetch is timed
  // from the last response, so a slow one is never cut short.
  useEffect(() => {
    if (!requests.some((r) => r.status === 'PENDING')) return
    const timer = setTimeout(() => setRefreshKey((k) => k + 1), PENDING_POLL_MS)
    return () => clearTimeout(timer)
  }, [requests])

  useRequestRefetch(
    requests.map((r) => r.id),
    (matchedIds) => {
      setRefreshKey((k) => k + 1)
      // the rows the pushed events named: their courier/arrival lines
      // may have moved, the untouched ones cannot have
      bumpDetails(matchedIds)
    },
  )

  function selectFilter(next: FilterOption) {
    if (next.key === filter.key) return
    setLoading(true)
    setFilter(next)
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
      reload(request)
    }
  }

  function reload(request: RequestSummary) {
    setRefreshKey((k) => k + 1)
    // only the acted-on card's detail can have changed
    bumpDetails([request.id])
  }

  // From the confirm dialog's link: the problem replaces the confirmation.
  function reportInstead(request: RequestSummary) {
    setPendingConfirm(null)
    setReporting(request)
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
        value={filter.key}
        onChange={selectFilter}
      />

      {notice && <Banner kind="success">{notice}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}

      {loading ? (
        <p className="text-sm text-gray-500">Loading your requests…</p>
      ) : requests.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            {filter.key === ALL_ACTIVE.key
              ? 'You have no active requests.'
              : `No ${filter.label.toLowerCase()} requests.`}
          </p>
          <p className="mt-1 text-sm text-gray-500">
            Finished requests are in{' '}
            <Link to="/history" className="underline hover:text-gray-900">
              History
            </Link>
            . Need something collected on campus? Create a request.
          </p>
        </div>
      ) : (
        <ul className="space-y-4">
          {requests.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              detailVersion={detailVersions[request.id] ?? 0}
              busy={busyId === request.id}
              now={now}
              onCancel={() => setPendingCancel(request)}
              onConfirm={() => setPendingConfirm(request)}
              onReport={() => setReporting(request)}
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
              `Request #${request.id} cancelled — ${credits(request.creditAmount)} released back to your balance. It is now in History.`,
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

      {pendingConfirm && (
        <ConfirmModal
          title="Confirm delivery?"
          confirmLabel="Confirm delivery"
          busyLabel="Confirming…"
          busy={busyId === pendingConfirm.id}
          onCancel={() => setPendingConfirm(null)}
          onConfirm={async () => {
            const request = pendingConfirm
            await run(
              request,
              () => orderApi.complete(request.id),
              `Delivery confirmed — ${credits(request.creditAmount)} paid to your courier. It is now in History.`,
            )
            setPendingConfirm(null)
          }}
        >
          <div className="space-y-3 text-sm text-gray-600">
            <p>
              Your courier will be paid{' '}
              <span className="font-semibold text-gray-900">
                {credits(pendingConfirm.creditAmount)}
              </span>{' '}
              for request #{pendingConfirm.id}. This can’t be undone.
            </p>
            {canReportProblem(pendingConfirm, now) && (
              <p>
                Wrong, damaged or missing item?{' '}
                <button
                  type="button"
                  onClick={() => reportInstead(pendingConfirm)}
                  className="font-medium text-gray-900 underline hover:text-gray-700"
                >
                  Report a problem
                </button>{' '}
                instead.
              </p>
            )}
          </div>
        </ConfirmModal>
      )}

      {reporting && (
        <DisputeModal
          request={reporting}
          onClose={() => {
            setReporting(null)
            // the dialog may have learned the request moved on (a 409)
            reload(reporting)
          }}
          onReported={() => {
            const request = reporting
            setReporting(null)
            setError(null)
            setNotice(
              `Problem reported for request #${request.id}. An admin will review it; the ${credits(request.creditAmount)} stay held until then.`,
            )
            reload(request)
          }}
        />
      )}
    </div>
  )
}

interface RequestCardProps {
  request: RequestSummary
  // bumped when something that could have changed *this* request
  // happened, so the expanded detail re-reads it
  detailVersion: number
  busy: boolean
  now: number // the ticking clock, for the countdown and the report button
  onCancel: () => void
  onConfirm: () => void
  onReport: () => void
}

// the states with an assigned courier to name
const IN_PROGRESS: RequestStatus[] = [
  'ACCEPTED',
  'COLLECTED',
  'ARRIVED',
  'DISPUTED',
]

function RequestCard({
  request,
  detailVersion,
  busy,
  now,
  onCancel,
  onConfirm,
  onReport,
}: RequestCardProps) {
  const dimmed =
    request.status === 'CANCELLED' ||
    request.status === 'EXPIRED' ||
    request.status === 'REJECTED'
  const outlineButton =
    'rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50'
  const detail = useDetail(
    request.id,
    IN_PROGRESS.includes(request.status),
    detailVersion,
  )
  const courier = useUsername(detail?.courierId ?? null)
  const autoAt = autoCompleteAt(request)
  const reportable = canReportProblem(request, now)
  const reportButton = (
    <button
      type="button"
      onClick={onReport}
      disabled={busy}
      className={outlineButton}
    >
      Report a problem
    </button>
  )

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
        {!isTerminal(request.status) &&
          request.status !== 'PENDING' &&
          request.status !== 'DISPUTED' && (
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
                (reportable
                  ? ` Auto-completes ${relativeTime(autoAt.toISOString(), now)} (at ${formatDateTime(autoAt.toISOString())}) unless you confirm or report a problem.`
                  : ' Completing automatically…')}
            </p>
            <div className="flex flex-wrap gap-2">
              {reportable && reportButton}
              <button
                type="button"
                onClick={onConfirm}
                disabled={busy}
                className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
              >
                {busy ? 'Working…' : 'Confirm delivery'}
              </button>
            </div>
          </div>
        )}

        {request.status === 'COLLECTED' && reportable && (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-gray-500">
              The deadline has passed and your item hasn’t arrived yet.
            </p>
            {reportButton}
          </div>
        )}

        {request.status === 'DISPUTED' && (
          <div className="space-y-1 text-sm">
            <p className="font-medium text-orange-700">
              ⚠ PROBLEM REPORTED
              {request.dispute &&
                ` — ${disputeCategoryLabels[request.dispute.category].toUpperCase()}`}
            </p>
            {request.dispute?.autoEscalated && (
              <p className="text-gray-500">
                Raised automatically: the item was not delivered by the
                deadline.
              </p>
            )}
            {request.dispute?.disputeNote && !request.dispute.autoEscalated && (
              <p className="text-gray-600">“{request.dispute.disputeNote}”</p>
            )}
            <p className="text-gray-500">
              An admin will review it and decide how the{' '}
              {credits(request.creditAmount)} are divided. They stay held until
              then.
            </p>
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

// The list items carry no courier, so an in-progress card reads its
// request's detail — again whenever this request is touched
// (a push naming it, an action on it), so the two can't drift apart.
function useDetail(
  requestId: number,
  enabled: boolean,
  version: number,
): RequestDetail | null {
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
  }, [requestId, enabled, version])
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
