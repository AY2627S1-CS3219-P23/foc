// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: in-memory stand-in for the credit calls the admin dashboard
// needs (balances per user, admin "Add Credits").
//
// TEMPORARY: credit-service has no API yet, so no paths are guessed
// here. Replace each function body with an apiFetch('credit', ...) call
// once it exists and delete the mock data — callers only depend on the
// adminCreditApi object's signatures.
// Reviewed by: [pending]

import type { CreditBalance } from './types'

// Keyed by the user ids in features/user/adminApi.ts's mock data.
const seedBalances: readonly CreditBalance[] = [
  { userId: 1, available: 0, reserved: 0 },
  { userId: 2, available: 15, reserved: 3 },
  { userId: 3, available: 28, reserved: 0 },
  { userId: 4, available: 5, reserved: 0 },
]

let balances: CreditBalance[] = seedBalances.map((b) => ({ ...b }))

function delay() {
  return new Promise((resolve) => setTimeout(resolve, 150))
}

export const adminCreditApi = {
  async listBalances(): Promise<CreditBalance[]> {
    await delay()
    return balances.map((b) => ({ ...b }))
  },

  async addCredits(userId: number, amount: number): Promise<CreditBalance> {
    await delay()
    let balance = balances.find((b) => b.userId === userId)
    if (!balance) {
      balance = { userId, available: 0, reserved: 0 }
      balances.push(balance)
    }
    balance.available += amount
    return { ...balance }
  },
}

// Test helper: restores the seed data between tests.
export function resetMockBalances() {
  balances = seedBalances.map((b) => ({ ...b }))
}
