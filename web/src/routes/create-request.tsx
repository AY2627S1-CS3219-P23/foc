// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Create Errand Request, per web/docs/wireframes/create-request.png —
// supplier, drop-off, item description, credit reward (stepper) and
// deadline, with the insufficient-credits / past-deadline checks listed
// before submit. These checks are a convenience: order-service and
// credit-service re-check on submit, and their refusal is shown as-is.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: posts order-service's
// real /create body — item, creditAmount, the supplier's id (as a number)
// and name, dropoffLocation, deadline. The request starts PENDING while
// credit-service reserves the reward, so the notice says so.
// 2026-10-26, Claude Code (Sonnet 5.5), issue #227 (N8): ?supplierId= preselects
// the pickup supplier (the Suppliers tab's Create Request button); an id
// that is not in the loaded list is dropped, so the form never submits a
// supplier it cannot name.
// 2026-10-26, issue #227 (N8.4): bookmarked suppliers are marked with a
// leading ★ (a native <option> cannot hold the flag icon); the list
// already arrives bookmarks-first from supplier-service.
// Author review: [pending]

import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import type { CreditBalance } from '@/features/credit/types'
import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import { credits } from '@/features/order/status'
import type { SupplierOption } from '@/features/order/types'
import { errorMessage } from '@/lib/api/http'

const MIN_REWARD = 1
const DEFAULT_REWARD = 3
const DEFAULT_LEAD_MS = 2 * 60 * 60 * 1000

// <input type="datetime-local"> wants local "YYYY-MM-DDTHH:mm".
function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const fieldClass =
  'w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 aria-invalid:border-red-400'

export function CreateRequest() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [suppliers, setSuppliers] = useState<SupplierOption[]>([])
  const [balance, setBalance] = useState<CreditBalance | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [supplierId, setSupplierId] = useState(
    () => searchParams.get('supplierId') ?? '',
  )
  const [dropoff, setDropoff] = useState('')
  const [item, setItem] = useState('')
  const [reward, setReward] = useState(DEFAULT_REWARD)
  const [deadline, setDeadline] = useState(() =>
    toLocalInput(new Date(Date.now() + DEFAULT_LEAD_MS)),
  )
  const [attempted, setAttempted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    orderApi
      .listSupplierOptions()
      .then((options) => {
        if (cancelled) return
        setSuppliers(options)
        setSupplierId((current) =>
          options.some((o) => o.id === current) ? current : '',
        )
      })
      .catch((err) => {
        if (!cancelled)
          setLoadError(
            errorMessage(err, 'Could not load suppliers. Try again.'),
          )
      })
    // Non-fatal: without a balance the form just skips its own check and
    // lets credit-service refuse an unaffordable reward.
    orderApi
      .getBalance()
      .then((b) => {
        if (!cancelled) setBalance(b)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // Re-evaluated every few seconds so a deadline that slips into the past
  // while the form sits open gets flagged without a keystroke.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15_000)
    return () => window.clearInterval(timer)
  }, [])

  const deadlineMs = new Date(deadline).getTime()
  const issues: string[] = []
  if (balance && reward > balance.available) {
    issues.push(
      `Insufficient credits. Your available balance (${balance.available}) is less than the reward (${reward}). Reduce the reward.`,
    )
  }
  if (!deadline || Number.isNaN(deadlineMs) || deadlineMs <= now) {
    issues.push('Deadline must be in the future.')
  }
  if (!Number.isInteger(reward) || reward < MIN_REWARD) {
    issues.push(
      `Reward must be a whole number of at least ${MIN_REWARD} credit.`,
    )
  }

  const missing = {
    supplier: attempted && !supplierId,
    dropoff: attempted && !dropoff.trim(),
    item: attempted && !item.trim(),
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setAttempted(true)
    setSubmitError(null)
    if (!supplierId || !dropoff.trim() || !item.trim() || issues.length > 0)
      return

    setSubmitting(true)
    try {
      const supplier = suppliers.find((s) => s.id === supplierId)
      const created = await orderApi.createRequest({
        supplierId: Number(supplierId),
        supplierName: supplier?.name ?? '',
        dropoffLocation: dropoff.trim(),
        item: item.trim(),
        creditAmount: reward,
        deadline: new Date(deadline).toISOString(),
      })
      navigate('/my-requests', {
        state: {
          notice: `Request #${created.id} created — reserving ${credits(reward)} from your balance.`,
        },
      })
    } catch (err) {
      setSubmitError(
        errorMessage(err, 'Could not create the request. Try again.'),
      )
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4">
      <Link
        to="/my-requests"
        className="text-sm text-gray-500 hover:text-gray-900"
      >
        ← My Requests
      </Link>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <form
          onSubmit={handleSubmit}
          noValidate
          className="space-y-5 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
        >
          <h1 className="text-xl font-semibold text-gray-900">
            Create New Errand Request
          </h1>

          {loadError && <Banner kind="error">{loadError}</Banner>}

          <div>
            <label
              htmlFor="supplier"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Pickup supplier
            </label>
            <select
              id="supplier"
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              aria-invalid={missing.supplier}
              className={fieldClass}
            >
              <option value="">Select a supplier…</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.bookmarked ? `★ ${s.name}` : s.name}
                </option>
              ))}
            </select>
            {missing.supplier && (
              <p className="mt-1 text-xs text-red-600">
                Choose where to pick up from.
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="dropoff"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Drop-off location
            </label>
            <input
              id="dropoff"
              value={dropoff}
              onChange={(e) => setDropoff(e.target.value)}
              placeholder="e.g. UTown Residence Hall 4, Lobby"
              maxLength={200}
              aria-invalid={missing.dropoff}
              className={fieldClass}
            />
            {missing.dropoff && (
              <p className="mt-1 text-xs text-red-600">
                Say where it should be delivered.
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="item"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Item
            </label>
            <textarea
              id="item"
              rows={3}
              value={item}
              onChange={(e) => setItem(e.target.value)}
              placeholder="e.g. 1x Iced Latte, 1x Croissant"
              maxLength={500}
              aria-invalid={missing.item}
              className={fieldClass}
            />
            {missing.item && (
              <p className="mt-1 text-xs text-red-600">
                Describe what to collect.
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="reward"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Credit reward
            </label>
            <div className="flex flex-wrap items-center gap-2">
              <input
                id="reward"
                type="number"
                min={MIN_REWARD}
                step={1}
                value={Number.isNaN(reward) ? '' : reward}
                onChange={(e) => setReward(e.target.valueAsNumber)}
                className={`${fieldClass} w-28`}
              />
              <button
                type="button"
                aria-label="Increase reward"
                onClick={() =>
                  setReward((r) => (Number.isNaN(r) ? MIN_REWARD : r + 1))
                }
                className="h-9 w-9 rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50"
              >
                +
              </button>
              <button
                type="button"
                aria-label="Decrease reward"
                onClick={() =>
                  setReward((r) =>
                    Math.max(
                      MIN_REWARD,
                      (Number.isNaN(r) ? MIN_REWARD : r) - 1,
                    ),
                  )
                }
                className="h-9 w-9 rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50"
              >
                −
              </button>
              <span className="text-xs text-gray-400">
                Min: {MIN_REWARD} credit
                {balance && ` · Available: ${balance.available}`}
              </span>
            </div>
          </div>

          <div>
            <label
              htmlFor="deadline"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Deadline
            </label>
            <input
              id="deadline"
              type="datetime-local"
              value={deadline}
              onChange={(e) => setDeadline(e.target.value)}
              className={fieldClass}
            />
            <p className="mt-1 text-xs text-gray-400">
              If no courier accepts by then, the request expires and your
              credits are refunded.
            </p>
          </div>

          {issues.length > 0 && (
            <ul
              aria-label="Fix before submitting"
              className="space-y-1 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800"
            >
              {issues.map((issue) => (
                <li key={issue}>⚠ {issue}</li>
              ))}
            </ul>
          )}
          {submitError && <Banner kind="error">{submitError}</Banner>}

          <button
            type="submit"
            disabled={submitting || issues.length > 0}
            className="w-full rounded-md bg-gray-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit Request'}
          </button>
        </form>

        <aside className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="font-semibold text-gray-900">
            How credit rewards work
          </h2>
          <ul className="mt-3 list-disc space-y-3 pl-5 text-sm text-gray-600">
            <li>
              When you submit a request, the credits you offer are reserved from
              your balance.
            </li>
            <li>
              Once a courier delivers and you confirm (or an hour passes), the
              credits go to the courier.
            </li>
            <li>
              If the request expires or you cancel before a courier accepts, the
              credits are fully refunded.
            </li>
          </ul>
        </aside>
      </div>
    </div>
  )
}
