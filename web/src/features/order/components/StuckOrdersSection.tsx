// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-27, issue #242.
// Scope: the admin dashboard's Stuck Orders section (DSP-11,
// docs/disputes.md): credit-service's reservations HELD longer than a
// threshold, checked against order-service in one batch status call, and
// flagged when the order is already finished (COMPLETED, CANCELLED,
// EXPIRED or RESOLVED). A DISPUTED order holds its credits legitimately
// and is never flagged. Each stuck order has a "Retry settlement" button
// (DSP-10), its 409s shown as order-service words them. Team decisions:
// the threshold presets (15 min, 1 h, 24 h, 7 days; 1 h by default),
// remembered per browser; the truncation notice.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'

import { creditApi } from '@/features/credit/api'
import type { HeldReservation } from '@/features/credit/types'
import { errorMessage } from '@/lib/api/http'
import { orderAdminApi } from '../adminApi'
import { orderApi } from '../api'
import { credits, formatDateTime, relativeTime } from '../status'
import type { RequestStatus } from '../types'
import { Banner } from './Banner'
import { StatusChip } from './StatusChip'

const STUCK_THRESHOLDS = [
  { value: 'PT15M', label: '15 minutes' },
  { value: 'PT1H', label: '1 hour' },
  { value: 'P1D', label: '24 hours' },
  { value: 'P7D', label: '7 days' },
] as const

type Threshold = (typeof STUCK_THRESHOLDS)[number]['value']

const DEFAULT_THRESHOLD: Threshold = 'PT1H'
export const STUCK_THRESHOLD_KEY = 'foc.admin.stuckThreshold'

// finished in order-service, so its credits should no longer be held
const FINISHED: RequestStatus[] = [
  'COMPLETED',
  'CANCELLED',
  'EXPIRED',
  'RESOLVED',
]

function isThreshold(value: string | null): value is Threshold {
  return STUCK_THRESHOLDS.some((t) => t.value === value)
}

// storage may be unavailable (private mode, blocked site data)
function savedThreshold(): Threshold {
  try {
    const saved = localStorage.getItem(STUCK_THRESHOLD_KEY)
    return isThreshold(saved) ? saved : DEFAULT_THRESHOLD
  } catch {
    return DEFAULT_THRESHOLD
  }
}

function saveThreshold(value: Threshold) {
  try {
    localStorage.setItem(STUCK_THRESHOLD_KEY, value)
  } catch {
    // the choice just isn't remembered
  }
}

interface StuckOrder {
  reservation: HeldReservation
  status: RequestStatus
}

type Load =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | {
      status: 'ready'
      held: number // reservations checked
      stuck: StuckOrder[]
      truncated: boolean
    }

// The held reservations and, for those whose order is finished, the
// order's state: one call to each service.
async function loadStuck(olderThan: Threshold): Promise<Load> {
  const held = await creditApi.getHeldReservations(olderThan)
  const statuses =
    held.items.length === 0
      ? {}
      : await orderAdminApi.getStatuses(held.items.map((r) => r.requestId))
  const stuck: StuckOrder[] = []
  for (const reservation of held.items) {
    const status = statuses[reservation.requestId]
    if (status && FINISHED.includes(status)) stuck.push({ reservation, status })
  }
  return {
    status: 'ready',
    held: held.items.length,
    stuck,
    truncated: held.truncated,
  }
}

export function StuckOrdersSection() {
  const [threshold, setThreshold] = useState<Threshold>(savedThreshold)
  const [reloadCount, setReloadCount] = useState(0)
  // the last load and the query it answers, so a newer query shows as
  // loading until its own answer arrives
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null)
  const [retrying, setRetrying] = useState<number | null>(null)
  // requests whose retry was accepted since the last load
  const [queued, setQueued] = useState<ReadonlySet<number>>(new Set())
  const [notice, setNotice] = useState<{
    kind: 'success' | 'error'
    text: string
  } | null>(null)

  const key = `${threshold}:${reloadCount}`
  const load: Load = result?.key === key ? result.load : { status: 'loading' }

  useEffect(() => {
    let cancelled = false
    loadStuck(threshold).then(
      (loaded) => {
        if (cancelled) return
        setResult({ key, load: loaded })
        setQueued(new Set())
      },
      (err: unknown) => {
        if (!cancelled) {
          setResult({
            key,
            load: {
              status: 'error',
              message: errorMessage(err, 'Could not check for stuck orders.'),
            },
          })
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [threshold, key])

  function chooseThreshold(value: Threshold) {
    saveThreshold(value)
    setThreshold(value)
    setNotice(null)
  }

  function refresh() {
    setNotice(null)
    setReloadCount((n) => n + 1)
  }

  async function retry(requestId: number) {
    setRetrying(requestId)
    setNotice(null)
    try {
      await orderAdminApi.retrySettlement(requestId)
      setQueued((ids) => new Set(ids).add(requestId))
      setNotice({
        kind: 'success',
        text: `Settlement for request #${requestId} sent again. Refresh in a moment to check that its credits were released.`,
      })
    } catch (err) {
      setNotice({
        kind: 'error',
        text: `Request #${requestId}: ${errorMessage(err, 'Could not retry its settlement.')}`,
      })
    } finally {
      setRetrying(null)
    }
  }

  const thresholdLabel =
    STUCK_THRESHOLDS.find((t) => t.value === threshold)?.label ?? threshold

  return (
    <section className="space-y-4" aria-labelledby="stuck-orders-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2
            id="stuck-orders-heading"
            className="text-lg font-semibold text-gray-900"
          >
            Stuck Orders
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            Finished orders whose credits are still held. Disputed orders hold
            their credits on purpose and are not listed.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="space-y-1">
            <label
              htmlFor="stuck-threshold"
              className="text-xs font-medium text-gray-500"
            >
              Held longer than
            </label>
            <select
              id="stuck-threshold"
              value={threshold}
              onChange={(e) => chooseThreshold(e.target.value as Threshold)}
              className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm focus:border-gray-400 focus:outline-none sm:w-36"
            >
              {STUCK_THRESHOLDS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            onClick={refresh}
            disabled={load.status === 'loading'}
            className="rounded-md border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Refresh
          </button>
        </div>
      </div>

      {notice && <Banner kind={notice.kind}>{notice.text}</Banner>}

      {load.status === 'loading' && (
        <p className="text-sm text-gray-500">Checking held credits…</p>
      )}
      {load.status === 'error' && <Banner kind="error">{load.message}</Banner>}
      {load.status === 'ready' && (
        <>
          {load.truncated && (
            <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              Showing the oldest 100; more are held.
            </p>
          )}
          {load.stuck.length === 0 ? (
            <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
              <p className="font-medium text-gray-900">No stuck orders.</p>
              <p className="mt-1 text-sm text-gray-500">
                {load.held === 0
                  ? `No credits have been held longer than ${thresholdLabel}.`
                  : `${load.held} held longer than ${thresholdLabel}, all for orders still in progress.`}
              </p>
            </div>
          ) : (
            <StuckOrdersTable
              orders={load.stuck}
              retrying={retrying}
              queued={queued}
              onRetry={retry}
            />
          )}
        </>
      )}
    </section>
  )
}

// The requester's username, or their id until (or unless) it loads.
function Requester({ id }: { id: string }) {
  const [name, setName] = useState<string | null>(null)
  useEffect(() => {
    let cancelled = false
    orderApi.getUsername(Number(id)).then(
      (username) => {
        if (!cancelled) setName(username)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [id])
  return <>{name ?? `User #${id}`}</>
}

interface StuckOrdersTableProps {
  orders: StuckOrder[]
  retrying: number | null
  queued: ReadonlySet<number>
  onRetry: (requestId: number) => void
}

function RetryButton({
  requestId,
  retrying,
  queued,
  onRetry,
}: { requestId: number } & Omit<StuckOrdersTableProps, 'orders'>) {
  if (queued.has(requestId)) {
    return <span className="text-sm text-gray-500">Retry sent</span>
  }
  return (
    <button
      type="button"
      onClick={() => onRetry(requestId)}
      disabled={retrying !== null}
      aria-label={`Retry settlement for request #${requestId}`}
      className="text-sm font-medium text-gray-900 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
    >
      {retrying === requestId ? 'Retrying…' : 'Retry settlement'}
    </button>
  )
}

function StuckOrdersTable({
  orders,
  retrying,
  queued,
  onRetry,
}: StuckOrdersTableProps) {
  return (
    <>
      {/* Desktop / tablet */}
      <div className="hidden overflow-x-auto rounded-lg border border-gray-200 bg-white md:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs font-semibold tracking-wide text-gray-500 uppercase">
            <tr>
              <th scope="col" className="px-4 py-3">
                Request
              </th>
              <th scope="col" className="px-4 py-3">
                Order status
              </th>
              <th scope="col" className="px-4 py-3">
                Requester
              </th>
              <th scope="col" className="px-4 py-3">
                Credits held
              </th>
              <th scope="col" className="px-4 py-3">
                Held since
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {orders.map(({ reservation: r, status }) => (
              <tr key={r.requestId}>
                <td className="px-4 py-3 text-gray-600 tabular-nums">
                  #{r.requestId}
                </td>
                <td className="px-4 py-3">
                  <StatusChip status={status} />
                </td>
                <td className="px-4 py-3 text-gray-900">
                  <Requester id={r.requesterId} />
                </td>
                <td className="px-4 py-3 text-gray-600">{credits(r.amount)}</td>
                <td className="px-4 py-3 text-gray-600">
                  {formatDateTime(r.heldSince)}{' '}
                  <span className="text-xs text-gray-500">
                    ({relativeTime(r.heldSince)})
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <RetryButton
                    requestId={Number(r.requestId)}
                    retrying={retrying}
                    queued={queued}
                    onRetry={onRetry}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile */}
      <ul className="space-y-3 md:hidden">
        {orders.map(({ reservation: r, status }) => (
          <li
            key={r.requestId}
            className="space-y-2 rounded-lg border border-gray-200 bg-white p-4"
          >
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-600 tabular-nums">
                #{r.requestId}
              </span>
              <StatusChip status={status} />
            </div>
            <dl className="grid grid-cols-2 gap-2 text-xs text-gray-600">
              <div>
                <dt className="text-gray-500">Requester</dt>
                <dd className="text-gray-900">
                  <Requester id={r.requesterId} />
                </dd>
              </div>
              <div>
                <dt className="text-gray-500">Credits held</dt>
                <dd>{credits(r.amount)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-gray-500">Held since</dt>
                <dd>
                  {formatDateTime(r.heldSince)} ({relativeTime(r.heldSince)})
                </dd>
              </div>
            </dl>
            <RetryButton
              requestId={Number(r.requestId)}
              retrying={retrying}
              queued={queued}
              onRetry={onRetry}
            />
          </li>
        ))}
      </ul>
    </>
  )
}
