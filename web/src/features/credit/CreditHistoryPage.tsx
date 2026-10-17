// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credits page (Credit F4.1, F4.1.2, F4.1.3), per
// web/docs/wireframes/credit-history.png: the balance from the shared
// poll, and GET /credits/me/history with paging and the amount and
// date-range filters. The current page reloads on each balance poll. A
// result is stamped with the query it answers
// (derive-by-key, as NotificationProvider does with its token), so a
// stale response never lands on a newer query.
// 2026-10-17, issue #196: GET /credits/me/expiries, for the balance
// card's line and the notice when credits expire within 7 days. PR #210
// review: read on load and on every balance poll (team decision), since
// a balance-change trigger missed changes that cancel out.
// 2026-10-17, issue #207 (PR #195 review nit): an empty result gives way
// to the loading line while the next query is out, so it never looks
// final.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'

import { Pagination } from '@/shared/components/Pagination'
import { errorMessage } from '@/lib/api/http'
import { creditApi } from './api'
import { BalanceCards } from './components/BalanceCards'
import { ExpiryNotice } from './components/ExpiryNotice'
import { nextExpiry } from './expiry'
import { HistoryFilters } from './components/HistoryFilters'
import { HistoryList } from './components/HistoryList'
import { NO_FILTERS, parseFilters, type HistoryFilterInputs } from './history'
import type { CreditExpiry, CreditHistoryEntry, CreditPage } from './types'
import { useCreditBalance } from './useCreditBalance'

// typing in a filter field applies once it pauses, as the admin user
// search does, so each keystroke is not a request
export const FILTER_DEBOUNCE_MS = 300

interface HistoryResult {
  // the query it answers, without the poll's version: a poll's reload
  // of the same query is not a new query
  key: string
  // whether that query had any filter, for the empty-state wording
  filtered: boolean
  page?: CreditPage<CreditHistoryEntry>
  error?: string
}

export function CreditHistoryPage() {
  const { state, version } = useCreditBalance()
  // the inputs as typed, checked at once; `applied` is what is queried
  const [filters, setFilters] = useState<HistoryFilterInputs>(NO_FILTERS)
  const [applied, setApplied] = useState<HistoryFilterInputs>(NO_FILTERS)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<HistoryResult | null>(null)
  // null until the first answer, and kept when a later read fails: the
  // expiry line is a hint, so a failure shows nothing rather than an error
  const [expiries, setExpiries] = useState<CreditExpiry[] | null>(null)

  // read on load and again on every poll (version goes up with each).
  // Keying on the balance missed changes that cancel out between polls,
  // such as the midnight sweep followed by a same-sized redistribution
  // share (PR #210 review, team decision).
  const expiriesKey = state?.status === 'ready' ? version : null

  useEffect(() => {
    if (expiriesKey === null) return
    let cancelled = false
    creditApi.getExpiries().then(
      (data) => {
        if (!cancelled) setExpiries(data.items)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [expiriesKey])

  useEffect(() => {
    if (filters === applied) return
    const timer = setTimeout(() => {
      setApplied(filters)
      setPage(0)
    }, FILTER_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [filters, applied])

  const parsed = parseFilters(filters)
  const appliedParsed = parseFilters(applied)
  const query = appliedParsed.ok ? appliedParsed.query : null
  const minAmount = query?.minAmount
  const maxAmount = query?.maxAmount
  const from = query?.from
  const to = query?.to
  const valid = query !== null
  const filtered = [minAmount, maxAmount, from, to].some((v) => v !== undefined)
  const key = JSON.stringify([minAmount, maxAmount, from, to, page])

  useEffect(() => {
    if (!valid) return
    let cancelled = false
    creditApi.getHistory({ page, minAmount, maxAmount, from, to }).then(
      (data) => {
        if (!cancelled) setResult({ key, filtered, page: data })
      },
      (err) => {
        if (!cancelled) {
          setResult((prev) =>
            // a failed reload of the rows already up keeps them
            prev?.key === key && prev.page
              ? prev
              : {
                  key,
                  filtered,
                  error: errorMessage(err, 'Could not load your transactions.'),
                },
          )
        }
      },
    )
    return () => {
      cancelled = true
    }
    // version: each balance poll reloads the rows already up
  }, [key, filtered, valid, page, minAmount, maxAmount, from, to, version])

  const changeFilters = (next: HistoryFilterInputs) => {
    setFilters(next)
    // clearing applies at once: there is nothing more to type
    if (next === NO_FILTERS) {
      setApplied(NO_FILTERS)
      setPage(0)
    }
  }

  const next = expiries ? nextExpiry(expiries) : null

  const loading = valid && result?.key !== key
  // the last rows stay up while the next page or filter loads
  const shown = result?.page
  const error = result?.key === key ? result.error : undefined

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-gray-900 md:text-3xl">
          Credit Transactions
        </h1>
        <p className="mt-1 text-sm text-gray-600">
          Verify your earned and reserved errand credits on NUS campus.
        </p>
      </header>

      <BalanceCards state={state ?? { status: 'loading' }} next={next} />

      {next?.soon && <ExpiryNotice next={next} />}

      <HistoryFilters
        value={filters}
        onChange={changeFilters}
        error={parsed.ok ? undefined : parsed.error}
      />

      <section
        aria-label="Transactions"
        aria-busy={loading}
        className="md:rounded-lg md:border md:border-gray-200 md:bg-white md:p-6 md:shadow-sm"
      >
        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
        {/* rows stay up (dimmed) while the next query loads; an empty
            result does not, or it would read as the new query's answer */}
        {!error && loading && (!shown || shown.content.length === 0) && (
          <p className="text-sm text-gray-500">Loading transactions…</p>
        )}
        {!error && !loading && shown && shown.content.length === 0 && (
          <p className="text-sm text-gray-500">
            {result?.filtered
              ? 'No transactions match these filters.'
              : 'No transactions yet.'}
          </p>
        )}
        {!error && shown && shown.content.length > 0 && (
          <div className={loading ? 'opacity-60' : undefined}>
            <HistoryList entries={shown.content} />
          </div>
        )}
        {shown && (
          <div className="mt-4">
            <Pagination
              page={shown.page + 1}
              totalPages={shown.totalPages}
              onPageChange={(p) => setPage(p - 1)}
              disabled={loading}
            />
          </div>
        )}
      </section>
    </div>
  )
}
