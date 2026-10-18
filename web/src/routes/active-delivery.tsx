// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Active Delivery — the assigned courier's view of one accepted
// request (mark picked up, mark arrived at drop-off, cancel before pickup,
// which reopens it), per web/docs/wireframes/active-delivery.png. The
// wireframe's chat panel belongs to a separate nice-to-have and is not
// built here. Anyone who isn't the courier sees the details read-only; the
// real gate is order-service refusing their calls.
// Author review: [pending]

import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { StatusChip } from '@/features/order/components/StatusChip'
import {
  autoCompleteAt,
  credits,
  formatDateTime,
} from '@/features/order/status'
import type { Order } from '@/features/order/types'
import { useCurrentUserId } from '@/features/order/useCurrentUserId'
import { errorMessage } from '@/lib/api/http'
import { ConfirmModal } from '@/shared/components/ConfirmModal'

// React Router keeps this component mounted when only :id changes (back/
// forward between two deliveries). Keying the view on the id remounts it,
// so the previous order's details, buttons, notices and errors can never
// show under the new URL.
export function ActiveDelivery() {
  const { id } = useParams()
  return <ActiveDeliveryView key={id} orderId={Number(id)} />
}

function ActiveDeliveryView({ orderId }: { orderId: number }) {
  const navigate = useNavigate()
  const userId = useCurrentUserId()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)

  useEffect(() => {
    let cancelled = false
    if (!Number.isInteger(orderId)) return
    orderApi
      .getOrder(orderId)
      .then((o) => {
        if (!cancelled) setOrder(o)
      })
      .catch((err) => {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load this request. Try again.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [orderId])

  // Orders the view's writes to `order`: every action and every recovery
  // refetch takes a number, and only the newest one (on a still-mounted
  // view) may update the screen, so a slow stale response can't revert a
  // newer result or land after the user has navigated away.
  const writeSeq = useRef(0)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  async function act(
    action: (orderId: number) => Promise<Order>,
    success: string,
  ) {
    if (!order) return
    setBusy(true)
    setError(null)
    try {
      const updated = await action(order.id)
      writeSeq.current++
      setOrder(updated)
      setNotice(success)
      return updated
    } catch (err) {
      setNotice(null)
      setError(errorMessage(err, 'That did not go through. Try again.'))
      // the order may have changed under us (requester cancelled, expired…)
      const seq = ++writeSeq.current
      orderApi.getOrder(order.id).then(
        (fresh) => {
          if (mounted.current && seq === writeSeq.current) setOrder(fresh)
        },
        () => {},
      )
    } finally {
      setBusy(false)
    }
  }

  if (loading && Number.isInteger(orderId)) {
    return <p className="text-sm text-gray-500">Loading delivery…</p>
  }
  if (!order) {
    return (
      <div className="space-y-4">
        <Banner kind="error">{error ?? 'That request does not exist.'}</Banner>
        <Link
          to="/requests"
          className="text-sm text-gray-500 hover:text-gray-900"
        >
          ← Back to requests
        </Link>
      </div>
    )
  }

  const isCourier = userId !== null && order.courierId === userId
  const autoAt = autoCompleteAt(order)
  const primary =
    'rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50'
  const outline =
    'rounded-md border border-gray-900 bg-white px-4 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:opacity-50'

  return (
    <div className="space-y-6">
      <Link
        to="/requests"
        className="text-sm text-gray-500 hover:text-gray-900"
      >
        ← Back to requests
      </Link>

      <div>
        <h1 className="text-2xl font-semibold text-gray-900">
          Active Delivery — Request #{order.id}
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          {isCourier
            ? 'Please pick up the items and deliver them to the student.'
            : 'You are viewing this request read-only.'}
        </p>
      </div>

      {notice && <Banner kind="success">{notice}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}

      <section className="max-w-3xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-3">
          <h2 className="font-semibold text-gray-900">Request details</h2>
          <StatusChip status={order.status} />
        </div>

        <dl className="mt-4 grid grid-cols-[6rem_1fr] gap-x-4 gap-y-3 text-sm">
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Item
          </dt>
          <dd className="text-gray-900">{order.description}</dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Reward
          </dt>
          <dd className="font-semibold text-gray-900">
            {credits(order.reward)}
          </dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Requester
          </dt>
          <dd className="text-gray-900">{order.requesterUsername}</dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Pickup from
          </dt>
          <dd className="text-gray-900">
            <span className="font-medium">{order.supplierName}</span>
            <span className="block text-gray-500">{order.pickupLocation}</span>
          </dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Drop off to
          </dt>
          <dd className="font-medium text-gray-900">{order.dropoffLocation}</dd>
        </dl>

        {isCourier && (
          <div className="mt-6 space-y-4 border-t border-gray-100 pt-5">
            {order.status === 'ACCEPTED' && (
              <>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      act(
                        orderApi.markPickedUp,
                        'Marked as picked up. Head to the drop-off.',
                      )
                    }
                    className={primary}
                  >
                    Mark as picked up
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => setConfirmingCancel(true)}
                    className={outline}
                  >
                    Cancel delivery
                  </button>
                </div>
                <p className="text-xs text-gray-400">
                  Cancelling is only possible before pickup. It returns the
                  request to open status so other couriers can take it.
                </p>
              </>
            )}

            {order.status === 'PICKED_UP' && (
              <div className="rounded-md border border-gray-200 bg-gray-50 p-4">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="font-semibold text-gray-900">
                    Item collected — en route to drop-off
                  </h3>
                  <span className="rounded bg-white px-2 py-0.5 text-xs font-medium text-gray-600">
                    EN ROUTE
                  </span>
                </div>
                <p className="mt-1 text-sm text-gray-500">
                  This will notify the requester to head to the drop-off
                  location.
                </p>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    act(
                      orderApi.markArrived,
                      'Marked as arrived. Waiting for the requester to confirm.',
                    )
                  }
                  className={`${primary} mt-3`}
                >
                  Mark as arrived at drop-off
                </button>
              </div>
            )}

            {order.status === 'DELIVERED' && (
              <p className="text-sm text-gray-600">
                Waiting for the requester to confirm delivery.
                {autoAt &&
                  ` If they don’t respond, it completes automatically at ${formatDateTime(autoAt.toISOString())}.`}
              </p>
            )}
            {order.status === 'COMPLETED' && (
              <p className="text-sm font-medium text-green-700">
                Delivery complete — {credits(order.reward)} added to your
                balance.
              </p>
            )}
            {order.status === 'DISPUTED' && (
              <p className="text-sm text-red-700">
                The requester disputed this delivery. An admin will review it.
              </p>
            )}
            {(order.status === 'OPEN' ||
              order.status === 'CANCELLED' ||
              order.status === 'EXPIRED') && (
              <p className="text-sm text-gray-500">
                This delivery is no longer assigned to you.
              </p>
            )}
          </div>
        )}
      </section>

      {confirmingCancel && (
        <ConfirmModal
          title="Cancel this delivery?"
          confirmLabel="Cancel delivery"
          busyLabel="Cancelling…"
          busy={busy}
          danger
          onCancel={() => setConfirmingCancel(false)}
          onConfirm={async () => {
            const updated = await act(
              orderApi.courierCancel,
              `You cancelled Request #${order.id}.`,
            )
            setConfirmingCancel(false)
            if (updated) navigate('/requests', { replace: true })
          }}
        >
          <p className="text-sm text-gray-600">
            Request #{order.id} returns to the open feed and you will no longer
            be assigned to it.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}
