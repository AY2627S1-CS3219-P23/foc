// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: context + hook exposing the signed-in user's credit balance,
// shared by the top-bar badge, the profile card and the credits page so
// one poll serves all three. Mirrors useNotifications' shape.
// Author review: Ryan Ang, pending pull request review.

import { createContext, useContext } from 'react'
import type { CreditBalance } from './types'

export type BalanceState =
  | { status: 'loading' }
  | { status: 'ready'; balance: CreditBalance }
  | { status: 'error'; message: string }

export interface CreditBalanceData {
  // null while logged out
  state: BalanceState | null
  // goes up each time a poll finds the balance changed, so the credits
  // page knows to reload its history rows
  version: number
}

export const CreditBalanceContext = createContext<CreditBalanceData | null>(
  null,
)

export const useCreditBalance = () => {
  const context = useContext(CreditBalanceContext)

  if (context === null) {
    throw new Error(
      'useCreditBalance must be used within a CreditBalanceProvider',
    )
  }

  return context
}
