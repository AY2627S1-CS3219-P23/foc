// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credit read API's two calls through the shared apiFetch
// wrapper, as an object so tests can spy on them (profileApi's shape).
// 2026-10-17: the two admin routes, for the admin dashboard's pool box.
// Author review: Ryan Ang, pending pull request review.

import { apiFetch } from '@/lib/api/http'
import type {
  CreditBalance,
  CreditExpiries,
  CreditHistoryEntry,
  CreditHistoryQuery,
  CreditPage,
  PoolSummary,
  RedistributionRun,
} from './types'

// URLSearchParams encodes a "+" offset as %2B, which the contract asks
// for; the page only sends toISOString()'s "Z" form anyway
function toQuery(query: CreditHistoryQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export const creditApi = {
  getBalance() {
    return apiFetch<CreditBalance>('credit', '/credits/me')
  },
  getHistory(query: CreditHistoryQuery = {}) {
    return apiFetch<CreditPage<CreditHistoryEntry>>(
      'credit',
      `/credits/me/history${toQuery(query)}`,
    )
  },
  // read on page load and when the balance changes, not on every poll
  getExpiries() {
    return apiFetch<CreditExpiries>('credit', '/credits/me/expiries')
  },
  // ADMIN and OWNER only; anyone else gets a 403
  getPoolSummary() {
    return apiFetch<PoolSummary>('credit', '/credits/admin/pool')
  },
  getRedistributions(query: { page?: number; size?: number } = {}) {
    return apiFetch<CreditPage<RedistributionRun>>(
      'credit',
      `/credits/admin/redistributions${toQuery(query)}`,
    )
  },
}
