// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174.
// Scope: the admin dashboard's common pool box (team decision): the
// pool's balance, the accounts a redistribution shares it among with a
// preview of each one's share, and the last and next redistribution,
// from GET /credits/admin/pool. Times are shown in Singapore time, the
// zone the redistribution runs in (D12), whatever the browser's zone.
// Loaded once when the dashboard opens.
// Same day, author's request: the past-distributions table under the
// figures, and the formatting moved to ../poolFormat for both.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { creditApi } from '../api'
import { credits, formatSgt, periodLabel } from '../poolFormat'
import type { PoolSummary } from '../types'
import { PastDistributions } from './PastDistributions'

type Load =
  | { status: 'loading' }
  | { status: 'ready'; summary: PoolSummary }
  | { status: 'error'; message: string }

function Stat({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint?: string
}) {
  return (
    <div className="rounded-md border border-gray-100 bg-gray-50 p-3">
      <dt className="text-xs font-medium tracking-wide text-gray-500 uppercase">
        {label}
      </dt>
      <dd className="mt-1 text-lg font-semibold text-gray-900">{value}</dd>
      {hint && <dd className="mt-0.5 text-xs text-gray-500">{hint}</dd>}
    </div>
  )
}

export function CommonPoolCard() {
  const [load, setLoad] = useState<Load>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    creditApi.getPoolSummary().then(
      (summary) => {
        if (!cancelled) setLoad({ status: 'ready', summary })
      },
      (err) => {
        if (!cancelled) {
          setLoad({
            status: 'error',
            message: errorMessage(err, 'Could not load the common pool.'),
          })
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="space-y-4" aria-labelledby="pool-heading">
      <div>
        <h2 id="pool-heading" className="text-lg font-semibold text-gray-900">
          Common Pool
        </h2>
        <p className="mt-1 text-sm text-gray-500">
          Expired credits collect here and are shared out to every account once
          a month.
        </p>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm md:p-6">
        {load.status === 'loading' && (
          <p className="text-sm text-gray-500">Loading the common pool…</p>
        )}
        {load.status === 'error' && (
          <p role="alert" className="text-sm text-red-700">
            {load.message}
          </p>
        )}
        {load.status === 'ready' && <PoolStats summary={load.summary} />}
        <div className="mt-6 border-t border-gray-100 pt-4">
          <PastDistributions />
        </div>
      </div>
    </section>
  )
}

function PoolStats({ summary }: { summary: PoolSummary }) {
  const { poolBalance, accounts, share, remainder, lastRun, nextRunAt } =
    summary

  const preview =
    accounts === 0
      ? 'No accounts to share among yet'
      : remainder > 0
        ? `${credits(share)} each, 1 more to ${remainder} of them`
        : `${credits(share)} each`

  return (
    <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Stat label="Pool balance" value={credits(poolBalance)} />
      <Stat
        label="Accounts to share among"
        value={String(accounts)}
        hint={`If shared now: ${preview}`}
      />
      <Stat
        label="Last distribution"
        value={lastRun ? periodLabel(lastRun.period) : 'None yet'}
        hint={
          lastRun
            ? `${credits(lastRun.distributed)} shared on ${formatSgt(lastRun.ranAt)}`
            : undefined
        }
      />
      <Stat label="Next distribution" value={formatSgt(nextRunAt)} />
    </dl>
  )
}
