// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: My Requests — the requester's own errands with a progress tracker,
// per web/docs/wireframes/my-requests.png. Cancel an open request, confirm
// or dispute a delivered one, re-release an expired/cancelled one. Every
// action is enforced by order-service; the buttons only mirror what it
// will accept. Completed requests live on the history page.
// Author review: [pending]

import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'

import { Banner } from '@/features/order/components/Banner'
import { DisputeModal } from '@/features/order/components/DisputeModal'
import { OrderProgress } from '@/features/order/components/OrderProgress'
import { StatusChip } from '@/features/order/components/StatusChip'
import { orderApi } from '@/features/order/api'
import {
  autoCompleteAt,
  credits,
  deadlineLine,
  formatDateTime,
  isTerminal,
} from '@/features/order/status'
import type { DisputeInput, Order } from '@/features/order/types'
import { errorMessage } from '@/lib/api/http'
import { ConfirmModal } from '@/shared/components/ConfirmModal'
import { Pagination } from '@/shared/components/Pagination'

const PAGE_SIZE = 10

export function MyRequests() {
  const location = useLocation()
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null,
  )
  const [busyId, setBusyId] = useState<number | null>(null)
  const [pendingCancel, setPendingCancel] = useState<Order | null>(null)
  const [disputing, setDisputing] = useState<Order | null>(null)
  const [disputeError, setDisputeError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    let cancelled = false
    orderApi
      .listMyOrders({
        as: 'requester',
        scope: 'current',
        page,
        size: PAGE_SIZE,
      })
      .then((result) => {
        if (cancelled) return
        setOrders(result.content)
        setTotalPages(result.totalPages)
        // an action can empty the last page (e.g. a completion leaves
        // the list); step back instead of showing a blank page
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
  }, [page, refreshKey])

  // The transition endpoints return the updated order, so a success patches
  // that row in place (a COMPLETED one leaves this page, which lists
  // everything but completed). Only the paths where the list's shape is
  // unknown refetch it: a failure ("see what the order really is now") and
  // re-release, which adds a new row.
  function applyUpdated(updated: Order) {
    const gone = updated.status === 'COMPLETED'
    setOrders((current) =>
      gone
        ? current.filter((o) => o.id !== updated.id)
        : current.map((o) => (o.id === updated.id ? updated : o)),
    )
    // that was the last row of a later page: step back, which refetches
    if (gone && page > 0 && orders.length === 1) setPage(page - 1)
  }

  async function run(
    order: Order,
    action: () => Promise<Order>,
    success: string,
    refetchOnSuccess = false,
  ) {
    setBusyId(order.id)
    setError(null)
    try {
      const updated = await action()
      setNotice(success)
      if (refetchOnSuccess) setRefreshKey((k) => k + 1)
      else applyUpdated(updated)
    } catch (err) {
      setNotice(null)
      setError(errorMessage(err, 'That did not go through. Try again.'))
      setRefreshKey((k) => k + 1)
    } finally {
      setBusyId(null)
    }
  }

  async function handleDispute(input: DisputeInput) {
    if (!disputing) return
    setBusyId(disputing.id)
    setDisputeError(null)
    try {
      const updated = await orderApi.disputeOrder(disputing.id, input)
      applyUpdated(updated)
      setNotice(
        `Dispute filed for Request #${disputing.id}. An admin will review it.`,
      )
      setDisputing(null)
    } catch (err) {
      setDisputeError(errorMessage(err, 'Could not file the dispute.'))
    } finally {
      setBusyId(null)
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

      {notice && <Banner kind="success">{notice}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}

      {loading ? (
        <p className="text-sm text-gray-500">Loading your requests…</p>
      ) : orders.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            You have no open or recent requests.
          </p>
          <p className="mt-1 text-sm text-gray-500">
            Need something collected on campus? Create your first request.
          </p>
        </div>
      ) : (
        <ul className="space-y-4">
          {orders.map((order) => (
            <RequestCard
              key={order.id}
              order={order}
              busy={busyId === order.id}
              onCancel={() => setPendingCancel(order)}
              onConfirm={() =>
                run(
                  order,
                  () => orderApi.confirmDelivery(order.id),
                  `Delivery confirmed — ${credits(order.reward)} paid to ${order.courierUsername}.`,
                )
              }
              onDispute={() => {
                setDisputeError(null)
                setDisputing(order)
              }}
              onReRelease={() =>
                run(
                  order,
                  () => orderApi.reReleaseOrder(order.id),
                  `Request #${order.id} re-released as a new request.`,
                  true,
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
            const order = pendingCancel
            await run(
              order,
              () => orderApi.cancelOrder(order.id),
              `Request #${order.id} cancelled — ${credits(order.reward)} released back to your balance.`,
            )
            setPendingCancel(null)
          }}
        >
          <p className="text-sm text-gray-600">
            Request #{pendingCancel.id} will be cancelled and the{' '}
            {credits(pendingCancel.reward)} reserved for it will be refunded.
          </p>
        </ConfirmModal>
      )}

      {disputing && (
        <DisputeModal
          orderId={disputing.id}
          busy={busyId === disputing.id}
          error={disputeError}
          onCancel={() => setDisputing(null)}
          onSubmit={handleDispute}
        />
      )}
    </div>
  )
}

interface RequestCardProps {
  order: Order
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
  onDispute: () => void
  onReRelease: () => void
}

function RequestCard({
  order,
  busy,
  onCancel,
  onConfirm,
  onDispute,
  onReRelease,
}: RequestCardProps) {
  const dimmed = order.status === 'CANCELLED' || order.status === 'EXPIRED'
  const autoAt = autoCompleteAt(order)
  const outlineButton =
    'rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50'

  return (
    <li
      className={`rounded-lg border border-gray-200 bg-white p-5 shadow-sm ${dimmed ? 'opacity-70' : ''}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="font-semibold text-gray-900">Request #{order.id}</h2>
          <StatusChip status={order.status} />
        </div>
        <span className="font-semibold whitespace-nowrap text-gray-900">
          {credits(order.reward)}
        </span>
      </div>

      <dl className="mt-4 grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
        <Field label="Item description" value={order.description} strong />
        <Field label="Deadline" value={deadlineLine(order)} />
        <Field label="Pickup from" value={order.supplierName} />
        <Field label="Drop off to" value={order.dropoffLocation} />
      </dl>

      {order.courierUsername && (
        <p className="mt-4 text-sm text-gray-600">
          Assigned courier:{' '}
          <span className="font-semibold text-gray-900">
            {order.courierUsername}
          </span>
        </p>
      )}

      <div className="mt-4 space-y-3 border-t border-gray-100 pt-4">
        {!isTerminal(order.status) && <OrderProgress status={order.status} />}

        {order.status === 'OPEN' && (
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

        {order.status === 'DELIVERED' && (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-gray-500">
              Your courier has arrived at the drop-off.
              {autoAt &&
                ` Auto-completes at ${formatDateTime(autoAt.toISOString())} if you don’t respond.`}
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onDispute}
                disabled={busy}
                className="rounded-md border border-gray-900 bg-white px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:opacity-50"
              >
                Dispute
              </button>
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

        {order.status === 'DISPUTED' && (
          <p className="text-sm text-red-700">
            Under dispute — credits stay reserved until an admin resolves it.
          </p>
        )}

        {(order.status === 'CANCELLED' || order.status === 'EXPIRED') && (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-medium text-gray-500">
              {order.status === 'CANCELLED'
                ? '✕ REQUEST CANCELLED — CREDITS REFUNDED'
                : '⚠ REQUEST EXPIRED — CREDITS REFUNDED'}
            </p>
            <button
              type="button"
              onClick={onReRelease}
              disabled={busy}
              className={outlineButton}
            >
              Re-release as new request
            </button>
          </div>
        )}
      </div>
    </li>
  )
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
