// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the one credit-service read the order pages need — the signed-in
// user's balance (GET /credits/me, credit-service's CreditController) —
// so the create-request form can warn about insufficient credits. Under
// VITE_MOCK_ORDER_API=true (dev server only) the balance comes from the
// order mock, which moves credits as orders are created/cancelled/settled.
// Author review: [pending]

import { apiFetch } from '@/lib/api/http'
import type { CreditBalance } from '@/features/order/types'

export function getBalance(): Promise<CreditBalance> {
  if (import.meta.env.DEV && import.meta.env.VITE_MOCK_ORDER_API === 'true') {
    return import('@/features/order/orderApiMock').then((m) =>
      m.mockOrderApi.getBalance(),
    )
  }
  return apiFetch<CreditBalance>('credit', '/credits/me')
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
  // read on page load and on every balance poll (PR #210 review)
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
