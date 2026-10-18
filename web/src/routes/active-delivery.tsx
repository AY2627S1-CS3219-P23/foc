// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Active Delivery — the assigned courier's view of one accepted
// request (mark picked up, mark arrived at drop-off, cancel before pickup,
// which reopens it), per web/docs/wireframes/active-delivery.png. The
// wireframe's chat panel belongs to a separate nice-to-have and is not
// built here. The requester sees the details read-only; the real gate is
// order-service refusing their calls.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: on order-service's real
// API — GET /requests/{id} (requester, courier or admin only; anyone else
// 404) and POST /collect, /arrive, /drop. Steps answer a message, so the
// request is reloaded after each; after a drop the courier can no longer
// read it, so the page goes back to the feed. The requester's name comes
// from user-service. States renamed (COLLECTED, ARRIVED); DISPUTED gone.
// Author review: [pending]

import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { StatusChip } from '@/features/order/components/StatusChip'
import {
  autoCompleteAt,
  credits,
  formatDateTime,
} from '@/features/order/status'
import type { RequestDetail } from '@/features/order/types'
import { useCurrentUserId } from '@/features/order/useCurrentUserId'
import { useUsername } from '@/features/order/useUsername'
import { errorMessage } from '@/lib/api/http'
import { ConfirmModal } from '@/shared/components/ConfirmModal'

// React Router keeps this component mounted when only :id changes (back/
// forward between two deliveries). Keying the view on the id remounts it,
// so the previous request's details, buttons, notices and errors can never
// show under the new URL.
export function ActiveDelivery() {
  const { id } = useParams()
  return <ActiveDeliveryView key={id} requestId={Number(id)} />
}

function ActiveDeliveryView({ requestId }: { requestId: number }) {
  const navigate = useNavigate()
  const userId = useCurrentUserId()
  const [request, setRequest] = useState<RequestDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmingDrop, setConfirmingDrop] = useState(false)
  const requester = useUsername(request?.requesterId ?? null)

  // Orders the view's writes to `request`: every load takes a number, and
  // only the newest one (on a still-mounted view) may update the screen,
  // so a slow stale response can't revert a newer result or land after
  // the user has navigated away.
  const loadSeq = useRef(0)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const reload = useCallback(() => {
    const seq = ++loadSeq.current
    return orderApi.getRequest(requestId).then(
      (fresh) => {
        if (mounted.current && seq === loadSeq.current) setRequest(fresh)
      },
      (err) => {
        if (mounted.current && seq === loadSeq.current) {
          setRequest(null)
          setError(errorMessage(err, 'Could not load this request. Try again.'))
        }
      },
    )
  }, [requestId])

  useEffect(() => {
    if (!Number.isInteger(requestId)) return
    reload().finally(() => {
      if (mounted.current) setLoading(false)
    })
  }, [requestId, reload])

  // Runs a step, then reloads the request: the step answers only a
  // message, and after a failure the request may have changed under us
  // (requester cancelled, deadline passed…).
  async function act(
    action: (id: number) => Promise<unknown>,
    success: string,
  ): Promise<boolean> {
    if (!request) return false
    setBusy(true)
    setError(null)
    let ok = false
    try {
      await action(request.id)
      setNotice(success)
      ok = true
    } catch (err) {
      setNotice(null)
      setError(errorMessage(err, 'That did not go through. Try again.'))
    } finally {
      setBusy(false)
    }
    await reload()
    return ok
  }

  if (loading && Number.isInteger(requestId)) {
    return <p className="text-sm text-gray-500">Loading delivery…</p>
  }
  if (!request) {
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

  const isCourier = userId !== null && request.courierId === userId
  const autoAt = autoCompleteAt(request)
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
          Active Delivery — Request #{request.id}
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          {isCourier
            ? 'Please pick up the item and deliver it to the student.'
            : 'You are viewing this request read-only.'}
        </p>
      </div>

      {notice && <Banner kind="success">{notice}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}

      <section className="max-w-3xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-3">
          <h2 className="font-semibold text-gray-900">Request details</h2>
          <StatusChip status={request.status} />
        </div>

        <dl className="mt-4 grid grid-cols-[6rem_1fr] gap-x-4 gap-y-3 text-sm">
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Item
          </dt>
          <dd className="text-gray-900">{request.item}</dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Reward
          </dt>
          <dd className="font-semibold text-gray-900">
            {credits(request.creditAmount)}
          </dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Requester
          </dt>
          <dd className="text-gray-900">{requester}</dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Pickup from
          </dt>
          <dd className="font-medium text-gray-900">{request.supplierName}</dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Drop off to
          </dt>
          <dd className="font-medium text-gray-900">
            {request.dropoffLocation}
          </dd>
          <dt className="text-xs font-medium tracking-wide text-gray-400 uppercase">
            Deadline
          </dt>
          <dd className="text-gray-900">{formatDateTime(request.deadline)}</dd>
        </dl>

        {isCourier && (
          <div className="mt-6 space-y-4 border-t border-gray-100 pt-5">
            {request.status === 'ACCEPTED' && (
              <>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      act(
                        orderApi.collect,
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
                    onClick={() => setConfirmingDrop(true)}
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

            {request.status === 'COLLECTED' && (
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
                      orderApi.arrive,
                      'Marked as arrived. Waiting for the requester to confirm.',
                    )
                  }
                  className={`${primary} mt-3`}
                >
                  Mark as arrived at drop-off
                </button>
              </div>
            )}

            {request.status === 'ARRIVED' && (
              <p className="text-sm text-gray-600">
                Waiting for the requester to confirm delivery.
                {autoAt &&
                  ` If they don’t respond, it completes automatically at ${formatDateTime(autoAt.toISOString())}.`}
              </p>
            )}
            {request.status === 'COMPLETED' && (
              <p className="text-sm font-medium text-green-700">
                Delivery complete — {credits(request.creditAmount)} added to
                your balance.
              </p>
            )}
            {request.status === 'EXPIRED' && (
              <p className="text-sm text-gray-500">
                The deadline passed before pickup, so this request expired.
              </p>
            )}
          </div>
        )}
      </section>

      {confirmingDrop && (
        <ConfirmModal
          title="Cancel this delivery?"
          confirmLabel="Cancel delivery"
          busyLabel="Cancelling…"
          busy={busy}
          danger
          onCancel={() => setConfirmingDrop(false)}
          onConfirm={async () => {
            setBusy(true)
            setError(null)
            try {
              await orderApi.drop(request.id)
              setConfirmingDrop(false)
              // no longer the courier, so the request can't be read here
              navigate('/requests', { replace: true })
            } catch (err) {
              setConfirmingDrop(false)
              setError(errorMessage(err, 'That did not go through. Try again.'))
              await reload()
            } finally {
              if (mounted.current) setBusy(false)
            }
          }}
        >
          <p className="text-sm text-gray-600">
            Request #{request.id} returns to the open feed and you will no
            longer be assigned to it.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}
