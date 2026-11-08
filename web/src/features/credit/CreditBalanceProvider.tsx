// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: layout route that keeps the signed-in user's balance current
// for the whole app. Team decision: poll GET /credits/me every 30 s
// while the tab is visible, pause while it is hidden, and fetch at once
// when it is shown again. Follows NotificationProvider's
// derive-by-token rule, so a logout or re-login starts over.
// 2026-11-08, Claude Code (Opus 5.5), Credit NFR1.1: a pushed request
// event after which a balance has changed (submitted, completed,
// cancelled, expired, dispute-resolved) fetches the balance at once and
// again 2 s later, since the push can arrive before the Credit Service
// has committed the change. The events and the second fetch are team
// decisions.
// Author review: Ryan Ang, pending pull request review.

import { useContext, useEffect, useMemo, useRef, useState } from 'react'
import { Outlet } from 'react-router'

import { errorMessage } from '@/lib/api/http'
import { NotificationsContext } from '../notifications/useNotifications'
import type { NotificationDto } from '../notifications/types'
import { useAuth } from '../user/useAuth'
import { creditApi } from './api'
import { BALANCE_EVENTS } from './balanceEvents'
import { CreditBalanceContext, type BalanceState } from './useCreditBalance'

export const BALANCE_POLL_MS = 30_000

// The Notification Service and the Credit Service read the same event
// in parallel, so a push can arrive before the change is committed: the
// balance is fetched on arrival and once more this long after.
export const PUSH_RECHECK_MS = 2_000

const NO_PUSHES: NotificationDto[] = []

// the balance stamped with the session that fetched it
interface SessionBalance {
  token: string
  state: BalanceState
  version: number
}

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

  // The current session's fetch, for the push effect below; null while
  // logged out.
  const loadRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    if (!accessToken) return

    let disposed = false
    let timer: ReturnType<typeof setInterval> | undefined
    // the newest load() started; showing the tab again can start one
    // while an interval load is still out, and only the newest may land
    let latest = 0

    const load = async () => {
      const request = ++latest
      try {
        const balance = await creditApi.getBalance()
        if (disposed || request !== latest) return
        setResult((prev) => {
          const mine = prev?.token === accessToken ? prev : null
          const polled = mine?.state.status === 'ready'
          return {
            token: accessToken,
            state: { status: 'ready', balance },
            // every poll after the first, not only a changed balance: a
            // reserve and a release between two polls leave the balance
            // as it was, yet add two history rows
            version: (mine?.version ?? 0) + (polled ? 1 : 0),
          }
        })
      } catch (err) {
        if (disposed || request !== latest) return
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

    loadRef.current = () => void load()
    void load()
    if (document.visibilityState === 'visible') startPolling()
    document.addEventListener('visibilitychange', onVisibilityChange)

    return () => {
      disposed = true
      loadRef.current = null
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [accessToken])

  // Outside a NotificationProvider (bare renders in tests) there are no
  // pushes, and the 30 s poll alone keeps the balance.
  const pushedNotifications =
    useContext(NotificationsContext)?.pushedNotifications ?? NO_PUSHES
  const recheck = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Seeded with the frames already there at mount: the first fetch is
  // newer than any of them. The provider only appends, so the fresh
  // frames are the new object references (as useRequestRefetch reads them).
  const consumed = useRef(pushedNotifications)
  useEffect(() => {
    if (pushedNotifications === consumed.current) return
    const fresh = pushedNotifications.filter(
      (n) => !consumed.current.includes(n),
    )
    consumed.current = pushedNotifications
    if (!fresh.some((n) => BALANCE_EVENTS.has(n.eventType ?? ''))) return
    // a hidden tab fetches when it is shown again
    if (document.visibilityState !== 'visible') return

    loadRef.current?.()
    // A burst moves the second fetch to 2 s after its last frame.
    if (recheck.current !== null) clearTimeout(recheck.current)
    recheck.current = setTimeout(() => {
      recheck.current = null
      if (document.visibilityState === 'visible') loadRef.current?.()
    }, PUSH_RECHECK_MS)
  }, [pushedNotifications])

  useEffect(
    () => () => {
      if (recheck.current !== null) clearTimeout(recheck.current)
      recheck.current = null
    },
    [],
  )

  const value = useMemo(() => ({ state, version }), [state, version])

  return (
    <CreditBalanceContext.Provider value={value}>
      <Outlet />
    </CreditBalanceContext.Provider>
  )
}
