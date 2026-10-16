// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credits page (Credit F4.1, F4.1.2, F4.1.3), per
// web/docs/wireframes/credit-history.png: the balance from the shared
// poll, and GET /credits/me/history with paging and the amount and
// date-range filters. The current page reloads when the poll finds the
// balance changed. A result is stamped with the query it answers
// (derive-by-key, as NotificationProvider does with its token), so a
// stale response never lands on a newer query.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'

import { Pagination } from '@/shared/components/Pagination'
import { errorMessage } from '@/lib/api/http'
import { creditApi } from './api'
import { BalanceCards } from './components/BalanceCards'
import { HistoryFilters } from './components/HistoryFilters'
import { HistoryList } from './components/HistoryList'
import { NO_FILTERS, parseFilters, type HistoryFilterInputs } from './history'
import type { CreditHistoryEntry, CreditPage } from './types'
import { useCreditBalance } from './useCreditBalance'

interface HistoryResult {
  key: string
  page?: CreditPage<CreditHistoryEntry>
  error?: string
}

export function CreditHistoryPage() {
  const { state, version } = useCreditBalance()
  const [filters, setFilters] = useState<HistoryFilterInputs>(NO_FILTERS)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<HistoryResult | null>(null)

  const parsed = parseFilters(filters)
  const query = parsed.ok ? parsed.query : null
  const minAmount = query?.minAmount
  const maxAmount = query?.maxAmount
  const from = query?.from
  const to = query?.to
  const valid = query !== null
  const key = JSON.stringify([minAmount, maxAmount, from, to, page, version])

  useEffect(() => {
    if (!valid) return
    let cancelled = false
    creditApi.getHistory({ page, minAmount, maxAmount, from, to }).then(
      (data) => {
        if (!cancelled) setResult({ key, page: data })
      },
      (err) => {
        if (!cancelled) {
          setResult({
            key,
            error: errorMessage(err, 'Could not load your transactions.'),
          })
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [key, valid, page, minAmount, maxAmount, from, to])

  const changeFilters = (next: HistoryFilterInputs) => {
    setFilters(next)
    setPage(0)
  }

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

      <BalanceCards state={state ?? { status: 'loading' }} />

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
        {!error && !shown && loading && (
          <p className="text-sm text-gray-500">Loading transactions…</p>
        )}
        {!error && shown && shown.content.length === 0 && (
          <p className="text-sm text-gray-500">
            {Object.values(filters).some((v) => v !== '')
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
