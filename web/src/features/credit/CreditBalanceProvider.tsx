// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: layout route that keeps the signed-in user's balance current
// for the whole app. Team decision: poll GET /credits/me every 30 s
// while the tab is visible, pause while it is hidden, and fetch at once
// when it is shown again. Follows NotificationProvider's
// derive-by-token rule, so a logout or re-login starts over.
// Author review: Ryan Ang, pending pull request review.

import { useEffect, useMemo, useState } from 'react'
import { Outlet } from 'react-router'

import { errorMessage } from '@/lib/api/http'
import { useAuth } from '../user/useAuth'
import { creditApi } from './api'
import type { CreditBalance } from './types'
import { CreditBalanceContext, type BalanceState } from './useCreditBalance'

export const BALANCE_POLL_MS = 30_000

// the balance stamped with the session that fetched it
interface SessionBalance {
  token: string
  state: BalanceState
  version: number
}

const sameBalance = (a: CreditBalance, b: CreditBalance) =>
  a.available === b.available && a.reserved === b.reserved

// one object, so the context value is stable while a first load is out
const LOADING: BalanceState = { status: 'loading' }

export const CreditBalanceProvider = () => {
  const { token } = useAuth()
  const accessToken = token?.accessToken ?? null
  const [result, setResult] = useState<SessionBalance | null>(null)

  const current = result?.token === accessToken ? result : null
  const state: BalanceState | null =
    accessToken === null ? null : (current?.state ?? LOADING)
  const version = current?.version ?? 0

  useEffect(() => {
    if (!accessToken) return

    let disposed = false
    let timer: ReturnType<typeof setInterval> | undefined

    const load = async () => {
      try {
        const balance = await creditApi.getBalance()
        if (disposed) return
        setResult((prev) => {
          const mine = prev?.token === accessToken ? prev : null
          const before =
            mine?.state.status === 'ready' ? mine.state.balance : null
          const changed = before !== null && !sameBalance(before, balance)
          return {
            token: accessToken,
            state: { status: 'ready', balance },
            version: (mine?.version ?? 0) + (changed ? 1 : 0),
          }
        })
      } catch (err) {
        if (disposed) return
        setResult((prev) => {
          const mine = prev?.token === accessToken ? prev : null
          // a failed poll keeps the last balance it had rather than
          // blanking the badge; only a first load reports the error
          if (mine?.state.status === 'ready') return mine
          return {
            token: accessToken,
            state: {
              status: 'error',
              message: errorMessage(err, 'Could not load your credits.'),
            },
            version: mine?.version ?? 0,
          }
        })
      }
    }

    const startPolling = () => {
      clearInterval(timer)
      timer = setInterval(() => void load(), BALANCE_POLL_MS)
    }

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void load()
        startPolling()
      } else {
        clearInterval(timer)
      }
    }

    void load()
    if (document.visibilityState === 'visible') startPolling()
    document.addEventListener('visibilitychange', onVisibilityChange)

    return () => {
      disposed = true
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [accessToken])

  const value = useMemo(() => ({ state, version }), [state, version])

  return (
    <CreditBalanceContext.Provider value={value}>
      <Outlet />
    </CreditBalanceContext.Provider>
  )
}
