// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the credit read API's bodies, transcribed from the "Read API
// contract" in docs/credit-service.md.
// 2026-10-17: the admin routes' bodies (the common pool box).
// Author review: Ryan Ang, pending pull request review.

// GET /credits/me; total is available + reserved
export interface CreditBalance {
  available: number
  reserved: number
  total: number
}

// the history types in docs/credit-service.md "Database"
export type CreditHistoryType =
  | 'PROVISION'
  | 'RESERVE'
  | 'RELEASE'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'EXPIRY'
  | 'REDISTRIBUTION'

// one row of GET /credits/me/history; amount is always positive
export interface CreditHistoryEntry {
  id: number
  type: CreditHistoryType
  amount: number
  requestRef: string | null
  occurredAt: string
}

// supplier-service's PageResponse shape, which the credit API reuses
export interface CreditPage<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// GET /credits/me/history's query; from/to are UTC instants, to exclusive
export interface CreditHistoryQuery {
  page?: number
  size?: number
  minAmount?: number
  maxAmount?: number
  from?: string
  to?: string
}

// one recorded month of GET /credits/admin/redistributions; distributed
// counts every credit taken from the pool, the remainder included
export interface RedistributionRun {
  period: string
  distributed: number
  remainder: number
  ranAt: string
}

// GET /credits/admin/pool (ADMIN and OWNER); share and remainder preview
// a redistribution now, lastRun is null before the first
export interface PoolSummary {
  poolBalance: number
  accounts: number
  share: number
  remainder: number
  lastRun: RedistributionRun | null
  nextRunAt: string
}
