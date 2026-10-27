// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-27, issue #242.
// Scope: the admin dashboard's Failed Orders section (view only): every
// user's REJECTED / CANCELLED / EXPIRED requests from GET /requests/admin,
// with why and when each closed, filtered by close reason, by one user
// (requester or courier) and by a created or closed date range, all
// combinable. Team decisions: RESOLVED is not a failure and stays out
// (its close reason is not offered); the user is picked by username,
// email or id through user-service's GET /users?search= and sent as an
// id; a request closed before closedAt was recorded shows its creation
// time, marked as such. Dates are the browser's local days, as on the
// credit history page.
// Author review: Ryan Ang, pending pull request review.

import { type FormEvent, useEffect, useState } from 'react'

import { localDayStart } from '@/features/credit/history'
import type { PagedResponse } from '@/features/supplier/types'
import { adminUserApi } from '@/features/user/adminApi'
import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'
import { orderAdminApi } from '../adminApi'
import { closeReasonLabels, credits, formatDateTime } from '../status'
import type { CloseReason, RequestStatus, RequestSummary } from '../types'
import { StatusChip } from './StatusChip'

export const FAILED_ORDERS_PAGE_SIZE = 20

const FAILED_STATES: RequestStatus[] = ['REJECTED', 'CANCELLED', 'EXPIRED']

// every reason a failed request can carry (DISPUTE_RESOLVED is RESOLVED's)
const REASONS: CloseReason[] = [
  'REQUESTER_CANCELLED',
  'RESERVATION_TIMEOUT',
  'INSUFFICIENT_CREDITS',
  'INVALID_REWARD',
  'DEADLINE_PASSED',
]

type DateField = 'closed' | 'created'

interface PickedUser {
  id: number
  username: string
}

interface Filters {
  user: PickedUser | null
  reason: CloseReason | ''
  dateField: DateField
  from: string // yyyy-mm-dd, or '' for none
  to: string
}

const NO_FILTERS: Filters = {
  user: null,
  reason: '',
  dateField: 'closed',
  from: '',
  to: '',
}

// The date inputs as the API's range (to is the exclusive start of the
// day after), or an error to show instead of querying.
function dateRange(
  from: string,
  to: string,
): { from?: string; to?: string } | { error: string } {
  const start = from ? localDayStart(from) : undefined
  const end = to ? localDayStart(to, 1) : undefined
  if (start === null || end === null) {
    return { error: 'Dates must be valid calendar dates.' }
  }
  // yyyy-mm-dd strings compare in date order
  if (from && to && from > to) {
    return { error: 'The start date cannot be after the end date.' }
  }
  return { from: start, to: end }
}

interface LoadResult {
  key: string
  data: PagedResponse<RequestSummary> | null
  error: string | null
}

export function FailedOrdersSection() {
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const [page, setPage] = useState(1) // 1-based; the API is 0-based
  const [result, setResult] = useState<LoadResult | null>(null)
  // the last page that loaded, kept on screen while the next one loads
  const [shown, setShown] = useState<PagedResponse<RequestSummary> | null>(null)
  const [userInput, setUserInput] = useState('')
  const [userError, setUserError] = useState<string | null>(null)
  const [findingUser, setFindingUser] = useState(false)

  const range = dateRange(filters.from, filters.to)
  const rangeError = 'error' in range ? range.error : null
  const key = JSON.stringify([filters, page])

  useEffect(() => {
    const dates = dateRange(filters.from, filters.to)
    if ('error' in dates) return
    let cancelled = false
    const closed = filters.dateField === 'closed'
    orderAdminApi
      .listRequests({
        page: page - 1,
        size: FAILED_ORDERS_PAGE_SIZE,
        state: FAILED_STATES,
        closeReason: filters.reason ? [filters.reason] : undefined,
        userId: filters.user?.id,
        createdFrom: closed ? undefined : dates.from,
        createdTo: closed ? undefined : dates.to,
        closedFrom: closed ? dates.from : undefined,
        closedTo: closed ? dates.to : undefined,
      })
      .then(
        (data) => {
          if (cancelled) return
          setResult({ key, data, error: null })
          setShown(data)
        },
        (err: unknown) => {
          if (!cancelled) {
            setResult({
              key,
              data: null,
              error: errorMessage(err, 'Could not load failed orders.'),
            })
          }
        },
      )
    return () => {
      cancelled = true
    }
  }, [filters, page, key])

  const loading = !rangeError && result?.key !== key
  const error = rangeError ?? (loading ? null : (result?.error ?? null))

  function update(change: Partial<Filters>) {
    setFilters((f) => ({ ...f, ...change }))
    setPage(1)
  }

  // Resolves the typed username, email or id to one account, removed
  // accounts included (their past orders still matter).
  async function handleUserSubmit(e: FormEvent) {
    e.preventDefault()
    const typed = userInput.trim()
    setUserError(null)
    if (!typed) {
      update({ user: null })
      return
    }
    setFindingUser(true)
    try {
      const found = await adminUserApi.listUsers({
        search: typed,
        includeDeleted: true,
        page: 0,
        size: 20,
      })
      const wanted = typed.toLowerCase()
      const match = found.content.find(
        (u) =>
          u.username.toLowerCase() === wanted ||
          u.email.toLowerCase() === wanted ||
          String(u.id) === typed,
      )
      if (match) {
        update({ user: { id: match.id, username: match.username } })
        setUserInput('')
      } else {
        setUserError(`No user matches "${typed}".`)
      }
    } catch (err) {
      setUserError(errorMessage(err, 'Could not look up that user.'))
    } finally {
      setFindingUser(false)
    }
  }

  const filtered =
    filters.user !== null ||
    filters.reason !== '' ||
    filters.from !== '' ||
    filters.to !== ''

  const inputClass =
    'w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none'

  return (
    <section className="space-y-4" aria-labelledby="failed-orders-heading">
      <div>
        <h2
          id="failed-orders-heading"
          className="text-lg font-semibold text-gray-900"
        >
          Failed Orders
        </h2>
        <p className="mt-1 text-sm text-gray-500">
          Requests that were rejected, cancelled or expired, and why.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
        <form onSubmit={handleUserSubmit} className="space-y-1">
          <label
            htmlFor="failed-orders-user"
            className="text-xs font-medium text-gray-500"
          >
            User (requester or courier)
          </label>
          {filters.user ? (
            <div className="flex items-center justify-between rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm">
              <span className="truncate font-medium text-gray-900">
                {filters.user.username}
              </span>
              <button
                type="button"
                onClick={() => update({ user: null })}
                aria-label={`Clear user ${filters.user.username}`}
                className="ml-2 text-gray-500 hover:text-gray-900"
              >
                ×
              </button>
            </div>
          ) : (
            <div className="flex gap-2">
              <input
                id="failed-orders-user"
                type="search"
                value={userInput}
                onChange={(e) => setUserInput(e.target.value)}
                placeholder="Username, email or ID"
                className={inputClass}
              />
              <button
                type="submit"
                disabled={findingUser}
                className="rounded-md border border-gray-200 px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {findingUser ? 'Finding…' : 'Find'}
              </button>
            </div>
          )}
          {userError && (
            <p role="alert" className="text-xs text-red-700">
              {userError}
            </p>
          )}
        </form>

        <div className="space-y-1">
          <label
            htmlFor="failed-orders-reason"
            className="text-xs font-medium text-gray-500"
          >
            Close reason
          </label>
          <select
            id="failed-orders-reason"
            value={filters.reason}
            onChange={(e) =>
              update({ reason: e.target.value as CloseReason | '' })
            }
            className={inputClass}
          >
            <option value="">All reasons</option>
            {REASONS.map((reason) => (
              <option key={reason} value={reason}>
                {closeReasonLabels[reason]}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label
            htmlFor="failed-orders-date-field"
            className="text-xs font-medium text-gray-500"
          >
            Date filter applies to
          </label>
          <select
            id="failed-orders-date-field"
            value={filters.dateField}
            onChange={(e) => update({ dateField: e.target.value as DateField })}
            className={inputClass}
          >
            <option value="closed">When it closed</option>
            <option value="created">When it was created</option>
          </select>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <div className="space-y-1">
            <label
              htmlFor="failed-orders-from"
              className="text-xs font-medium text-gray-500"
            >
              From
            </label>
            <input
              id="failed-orders-from"
              type="date"
              value={filters.from}
              onChange={(e) => update({ from: e.target.value })}
              className={inputClass}
            />
          </div>
          <div className="space-y-1">
            <label
              htmlFor="failed-orders-to"
              className="text-xs font-medium text-gray-500"
            >
              To
            </label>
            <input
              id="failed-orders-to"
              type="date"
              value={filters.to}
              onChange={(e) => update({ to: e.target.value })}
              className={inputClass}
            />
          </div>
        </div>
      </div>

      {filters.dateField === 'closed' && (filters.from || filters.to) && (
        <p className="text-xs text-gray-500">
          Orders closed before close times were recorded have no close time, so
          a closed-date filter leaves them out.
        </p>
      )}

      {error && (
        <p
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}

      {!shown ? (
        loading && (
          <p className="text-sm text-gray-500">Loading failed orders…</p>
        )
      ) : shown.content.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            {filtered
              ? 'No failed orders match these filters.'
              : 'No failed orders.'}
          </p>
        </div>
      ) : (
        <>
          <FailedOrdersTable requests={shown.content} />
          <Pagination
            page={page}
            totalPages={shown.totalPages}
            onPageChange={setPage}
            disabled={loading}
          />
        </>
      )}
    </section>
  )
}

// When the request closed, or its creation time marked as such for a
// request closed before closedAt was recorded.
function ClosedTime({ request }: { request: RequestSummary }) {
  if (request.closedAt) return <>{formatDateTime(request.closedAt)}</>
  return (
    <>
      {formatDateTime(request.createdAt)}{' '}
      <span className="text-xs text-gray-500">
        (created; close time unknown)
      </span>
    </>
  )
}

function reasonOf(request: RequestSummary): string {
  return request.closeReason
    ? closeReasonLabels[request.closeReason]
    : 'Not recorded'
}

function FailedOrdersTable({ requests }: { requests: RequestSummary[] }) {
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
                Status
              </th>
              <th scope="col" className="px-4 py-3">
                Reason
              </th>
              <th scope="col" className="px-4 py-3">
                Item
              </th>
              <th scope="col" className="px-4 py-3">
                Reward
              </th>
              <th scope="col" className="px-4 py-3">
                Closed
              </th>
              <th scope="col" className="px-4 py-3">
                Deadline
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {requests.map((r) => (
              <tr key={r.id}>
                <td className="px-4 py-3 text-gray-600 tabular-nums">
                  #{r.id}
                </td>
                <td className="px-4 py-3">
                  <StatusChip status={r.status} />
                </td>
                <td className="px-4 py-3 text-gray-900">{reasonOf(r)}</td>
                <td className="px-4 py-3 text-gray-600">
                  <span className="text-gray-900">{r.item}</span>
                  <span className="block text-xs">
                    {r.supplierName} → {r.dropoffLocation}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-600">
                  {credits(r.creditAmount)}
                </td>
                <td className="px-4 py-3 text-gray-600">
                  <ClosedTime request={r} />
                </td>
                <td className="px-4 py-3 text-gray-600">
                  {formatDateTime(r.deadline)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile */}
      <ul className="space-y-3 md:hidden">
        {requests.map((r) => (
          <li
            key={r.id}
            className="space-y-2 rounded-lg border border-gray-200 bg-white p-4"
          >
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-600 tabular-nums">
                #{r.id}
              </span>
              <StatusChip status={r.status} />
            </div>
            <p className="text-sm font-medium text-gray-900">{reasonOf(r)}</p>
            <p className="text-sm text-gray-900">
              {r.item}
              <span className="block text-xs text-gray-600">
                {r.supplierName} → {r.dropoffLocation}
              </span>
            </p>
            <dl className="grid grid-cols-2 gap-2 text-xs text-gray-600">
              <div>
                <dt className="text-gray-500">Reward</dt>
                <dd>{credits(r.creditAmount)}</dd>
              </div>
              <div>
                <dt className="text-gray-500">Deadline</dt>
                <dd>{formatDateTime(r.deadline)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-gray-500">Closed</dt>
                <dd>
                  <ClosedTime request={r} />
                </dd>
              </div>
            </dl>
          </li>
        ))}
      </ul>
    </>
  )
}
