// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174.
// Scope: the admin dashboard's past-distributions table (author's
// request), from GET /credits/admin/redistributions: one row per
// recorded month, newest first, with the shared pager. A result is
// stamped with the page it answers, as the credits page does, so a slow
// response never lands on a newer page.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'

import { Pagination } from '@/shared/components/Pagination'
import { errorMessage } from '@/lib/api/http'
import { creditApi } from '../api'
import { credits, formatSgt, periodLabel } from '../poolFormat'
import type { CreditPage, RedistributionRun } from '../types'

// a year of monthly runs per page
export const PAST_RUNS_PAGE_SIZE = 12

interface Result {
  page: number
  runs?: CreditPage<RedistributionRun>
  error?: string
}

export function PastDistributions() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    let cancelled = false
    creditApi.getRedistributions({ page, size: PAST_RUNS_PAGE_SIZE }).then(
      (runs) => {
        if (!cancelled) setResult({ page, runs })
      },
      (err) => {
        if (!cancelled) {
          setResult({
            page,
            error: errorMessage(err, 'Could not load past distributions.'),
          })
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [page])

  const loading = result?.page !== page
  // the last rows stay up while the next page loads
  const shown = result?.runs
  const error = result?.page === page ? result.error : undefined

  return (
    <div aria-labelledby="past-runs-heading" role="group">
      <h3
        id="past-runs-heading"
        className="text-sm font-semibold text-gray-900"
      >
        Past distributions
      </h3>

      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {!error && !shown && loading && (
        <p className="mt-2 text-sm text-gray-500">Loading…</p>
      )}
      {!error && shown && shown.content.length === 0 && (
        <p className="mt-2 text-sm text-gray-500">No distributions yet.</p>
      )}
      {!error && shown && shown.content.length > 0 && (
        <div className={`mt-2 overflow-x-auto ${loading ? 'opacity-60' : ''}`}>
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-xs font-semibold tracking-wide text-gray-600 uppercase">
                <th scope="col" className="py-2 pr-4">
                  Month
                </th>
                <th scope="col" className="py-2 pr-4 text-right">
                  Shared
                </th>
                <th scope="col" className="py-2 pr-4 text-right">
                  Remainder
                </th>
                <th scope="col" className="py-2 text-right">
                  Run at
                </th>
              </tr>
            </thead>
            <tbody>
              {shown.content.map((run) => (
                <tr key={run.period} className="border-b border-gray-100">
                  <td className="py-2 pr-4 whitespace-nowrap text-gray-900">
                    {periodLabel(run.period)}
                  </td>
                  <td className="py-2 pr-4 text-right whitespace-nowrap text-gray-900">
                    {credits(run.distributed)}
                  </td>
                  <td className="py-2 pr-4 text-right whitespace-nowrap text-gray-600">
                    {run.remainder}
                  </td>
                  <td className="py-2 text-right whitespace-nowrap text-gray-600">
                    {formatSgt(run.ranAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {shown && (
        <div className="mt-3">
          <Pagination
            page={shown.page + 1}
            totalPages={shown.totalPages}
            onPageChange={(p) => setPage(p - 1)}
            disabled={loading}
          />
        </div>
      )}
    </div>
  )
}
