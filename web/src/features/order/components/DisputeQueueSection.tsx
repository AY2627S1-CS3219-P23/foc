// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-29, issue #241.
// Scope: the admin dashboard's Disputes section (team decisions DSP-3,
// DSP-5, DSP-7 and DSP-8, docs/disputes.md): the DISPUTED requests from
// GET /requests/admin?state=DISPUTED with a count badge (pull, not push),
// each with its category, note and whether the sweep raised it; and a
// resolve dialog that pays the courier, refunds the requester or splits
// the reward, the two shares always adding up to it. order-service's 409
// (the admin is a party to the order, or it is no longer DISPUTED) is
// shown in the dialog as it words it.
// Author review: Ryan Ang, pending pull request review.

import { type FormEvent, useEffect, useState } from 'react'

import type { PagedResponse } from '@/features/supplier/types'
import { errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { Pagination } from '@/shared/components/Pagination'
import { orderAdminApi } from '../adminApi'
import {
  credits,
  disputeCategoryLabels,
  formatDateTime,
  MAX_DISPUTE_NOTE,
  relativeTime,
} from '../status'
import type { RequestDetail, RequestSummary } from '../types'
import { useUsername } from '../useUsername'
import { Banner } from './Banner'

export const DISPUTES_PAGE_SIZE = 20

type Load =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: PagedResponse<RequestSummary> }

export function DisputeQueueSection() {
  const [page, setPage] = useState(0)
  const [reloadCount, setReloadCount] = useState(0)
  // the last load and the query it answers, so a newer query shows as
  // loading until its own answer arrives
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null)
  const [resolving, setResolving] = useState<RequestSummary | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const key = `${page}:${reloadCount}`
  const load: Load = result?.key === key ? result.load : { status: 'loading' }

  useEffect(() => {
    let cancelled = false
    orderAdminApi.listDisputes(page, DISPUTES_PAGE_SIZE).then(
      (loaded) => {
        if (cancelled) return
        // a resolve can empty the last page; step back instead
        if (loaded.content.length === 0 && page > 0) setPage(page - 1)
        else setResult({ key, load: { status: 'ready', page: loaded } })
      },
      (err: unknown) => {
        if (!cancelled) {
          setResult({
            key,
            load: {
              status: 'error',
              message: errorMessage(err, 'Could not load the disputes.'),
            },
          })
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [page, key])

  function refresh() {
    setNotice(null)
    setReloadCount((n) => n + 1)
  }

  const count = load.status === 'ready' ? load.page.totalElements : null

  return (
    <section className="space-y-4" aria-labelledby="disputes-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2
            id="disputes-heading"
            className="flex items-center gap-2 text-lg font-semibold text-gray-900"
          >
            Disputes
            {count !== null && count > 0 && (
              <span
                aria-label={`${count} open`}
                className="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700"
              >
                {count}
              </span>
            )}
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            Orders a requester reported a problem with, or that were not
            delivered by their deadline. Their credits stay held until you
            resolve them.
          </p>
        </div>
        <button
          type="button"
          onClick={refresh}
          disabled={load.status === 'loading'}
          className="self-start rounded-md border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 sm:self-auto"
        >
          Refresh
        </button>
      </div>

      {notice && <Banner kind="success">{notice}</Banner>}

      {load.status === 'loading' && (
        <p className="text-sm text-gray-500">Loading disputes…</p>
      )}
      {load.status === 'error' && <Banner kind="error">{load.message}</Banner>}
      {load.status === 'ready' &&
        (load.page.content.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
            <p className="font-medium text-gray-900">No open disputes.</p>
          </div>
        ) : (
          <>
            <ul className="space-y-3">
              {load.page.content.map((request) => (
                <DisputeCard
                  key={request.id}
                  request={request}
                  onResolve={() => {
                    setNotice(null)
                    setResolving(request)
                  }}
                />
              ))}
            </ul>
            {load.page.totalPages > 1 && (
              <div className="flex justify-end">
                <Pagination
                  page={page + 1}
                  totalPages={load.page.totalPages}
                  onPageChange={(p) => setPage(p - 1)}
                />
              </div>
            )}
          </>
        ))}

      {resolving && (
        <ResolveModal
          request={resolving}
          onClose={() => setResolving(null)}
          onResolved={(text) => {
            setResolving(null)
            setNotice(text)
            setReloadCount((n) => n + 1)
          }}
        />
      )}
    </section>
  )
}

function DisputeCard({
  request,
  onResolve,
}: {
  request: RequestSummary
  onResolve: () => void
}) {
  const dispute = request.dispute
  return (
    <li className="space-y-3 rounded-lg border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold text-gray-900 tabular-nums">
            #{request.id}
          </span>
          {dispute && (
            <span className="rounded-full bg-orange-50 px-2 py-0.5 text-xs font-medium text-orange-700">
              {disputeCategoryLabels[dispute.category]}
            </span>
          )}
          {dispute?.autoEscalated && (
            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
              Auto-escalated
            </span>
          )}
        </div>
        <span className="text-sm font-semibold text-gray-900">
          {credits(request.creditAmount)}
        </span>
      </div>

      <dl className="grid gap-x-6 gap-y-2 text-xs text-gray-600 sm:grid-cols-3">
        <div>
          <dt className="text-gray-500">Item</dt>
          <dd className="text-sm text-gray-900">{request.item}</dd>
        </div>
        <div>
          <dt className="text-gray-500">Route</dt>
          <dd className="text-sm text-gray-900">
            {request.supplierName} → {request.dropoffLocation}
          </dd>
        </div>
        <div>
          <dt className="text-gray-500">Reported</dt>
          <dd className="text-sm text-gray-900">
            {dispute
              ? `${formatDateTime(dispute.raisedAt)} (${relativeTime(dispute.raisedAt)})`
              : '—'}
          </dd>
        </div>
      </dl>

      {dispute?.disputeNote && (
        <p className="rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-700">
          {dispute.disputeNote}
        </p>
      )}

      <div className="flex justify-end">
        <button
          type="button"
          onClick={onResolve}
          aria-label={`Resolve request #${request.id}`}
          className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800"
        >
          Resolve
        </button>
      </div>
    </li>
  )
}

type Outcome = 'COURIER' | 'REQUESTER' | 'SPLIT'

const OUTCOMES: { value: Outcome; label: string }[] = [
  { value: 'COURIER', label: 'Pay the courier' },
  { value: 'REQUESTER', label: 'Refund the requester' },
  { value: 'SPLIT', label: 'Split the reward' },
]

// The courier's share for an outcome, or null while a split's is invalid.
function courierShare(
  outcome: Outcome,
  split: string,
  reward: number,
): number | null {
  if (outcome === 'COURIER') return reward
  if (outcome === 'REQUESTER') return 0
  if (!/^\d+$/.test(split)) return null
  const n = Number(split)
  return n <= reward ? n : null
}

function ResolveModal({
  request,
  onClose,
  onResolved,
}: {
  request: RequestSummary
  onClose: () => void
  onResolved: (notice: string) => void
}) {
  const reward = request.creditAmount
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [split, setSplit] = useState('')
  const [note, setNote] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // the parties come from the detail; the list item carries no user ids
  const [detail, setDetail] = useState<RequestDetail | null>(null)
  const [detailFailed, setDetailFailed] = useState(false)
  const requester = useUsername(detail?.requesterId ?? null)
  const courier = useUsername(detail?.courierId ?? null)

  useEffect(() => {
    let cancelled = false
    orderAdminApi.getRequest(request.id).then(
      (d) => {
        if (!cancelled) setDetail(d)
      },
      () => {
        if (!cancelled) setDetailFailed(true)
      },
    )
    return () => {
      cancelled = true
    }
  }, [request.id])

  // shown until the detail names the parties
  const party = detailFailed ? 'Could not load' : '…'

  const toCourier =
    outcome === null ? null : courierShare(outcome, split, reward)
  const outcomeError =
    submitted && outcome === null ? 'Choose how to resolve it.' : null
  const splitError =
    outcome === 'SPLIT' && (submitted || split !== '') && toCourier === null
      ? `Enter a whole number from 0 to ${reward}.`
      : null
  const noteError =
    note.length > MAX_DISPUTE_NOTE
      ? `Keep the note to ${MAX_DISPUTE_NOTE} characters.`
      : null

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    if (toCourier === null || noteError) return
    const trimmed = note.trim()
    setBusy(true)
    setError(null)
    try {
      await orderAdminApi.resolve(request.id, {
        courierAmount: toCourier,
        requesterAmount: reward - toCourier,
        ...(trimmed ? { note: trimmed } : {}),
      })
      onResolved(
        `Request #${request.id} resolved: ${credits(toCourier)} to the courier, ${credits(reward - toCourier)} back to the requester.`,
      )
    } catch (err) {
      setError(errorMessage(err, 'Could not resolve the dispute. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Resolve request #${request.id}`} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <div>
            <dt className="text-xs text-gray-500">Requester</dt>
            <dd className="text-gray-900">{requester ?? party}</dd>
          </div>
          <div>
            <dt className="text-xs text-gray-500">Courier</dt>
            <dd className="text-gray-900">{courier ?? party}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs text-gray-500">Reward held</dt>
            <dd className="font-semibold text-gray-900">{credits(reward)}</dd>
          </div>
        </dl>

        {error && <Banner kind="error">{error}</Banner>}

        <fieldset>
          <legend className="text-sm font-medium text-gray-900">Outcome</legend>
          <div className="mt-2 space-y-2">
            {OUTCOMES.map((o) => (
              <label
                key={o.value}
                className="flex cursor-pointer items-center gap-2 text-sm text-gray-700"
              >
                <input
                  type="radio"
                  name="resolve-outcome"
                  value={o.value}
                  checked={outcome === o.value}
                  onChange={() => setOutcome(o.value)}
                />
                {o.label}
              </label>
            ))}
          </div>
          {outcomeError && (
            <p className="mt-1 text-xs text-red-600">{outcomeError}</p>
          )}
        </fieldset>

        {outcome === 'SPLIT' && (
          // indented under "Split the reward", the option it belongs to
          <div className="ml-6 space-y-1">
            <label
              htmlFor="resolve-courier-share"
              className="text-sm font-medium text-gray-900"
            >
              Credits to the courier
            </label>
            <input
              id="resolve-courier-share"
              type="number"
              inputMode="numeric"
              min={0}
              max={reward}
              step={1}
              value={split}
              onChange={(e) => setSplit(e.target.value)}
              aria-invalid={splitError !== null}
              className="w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none sm:w-32"
            />
            {splitError ? (
              <p className="text-xs text-red-600">{splitError}</p>
            ) : (
              toCourier !== null && (
                <p className="text-xs text-gray-500">
                  {credits(reward - toCourier)} back to the requester.
                </p>
              )
            )}
          </div>
        )}

        <div>
          <label
            htmlFor="resolve-note"
            className="text-sm font-medium text-gray-900"
          >
            Note <span className="font-normal text-gray-500">(optional)</span>
          </label>
          <textarea
            id="resolve-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={3}
            aria-invalid={noteError !== null}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
          />
          <div className="flex justify-between gap-2 text-xs">
            <span className="text-red-600">{noteError}</span>
            <span className={noteError ? 'text-red-600' : 'text-gray-500'}>
              {note.length}/{MAX_DISPUTE_NOTE}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {busy ? 'Resolving…' : 'Resolve'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
