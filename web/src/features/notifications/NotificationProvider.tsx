// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: the single STOMP client of the whole app (design decision D2 /
// "Session mechanics", docs/notification-service.md), per the author's
// decisions (options Q&A): native WebSocket only, exponential reconnect
// 1 s doubling to a 30 s cap, badge-only UI until issue #66. Layout
// route below AuthProvider, mirroring its provider pattern.
// 2026-10-16, PR #185 re-review (@Sinnez1): ERROR-frame comment
// softened (the server sends one for any frame-handling failure, not
// only CONNECT auth); unused NotificationDto export dropped until #66
// reads the body.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1): an
// ERROR frame no longer kills push silently — bounded retry, then the
// session is marked stopped so the bell can say so (chosen by Leong Wei
// Zhi from four options via neutral options Q&A).
// Reviewed by: Leong Wei Zhi (via pull request).

import { useEffect, useMemo, useState } from 'react'
import { Outlet } from 'react-router'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { serviceBaseUrls } from '@/lib/api/config'
import { useAuth } from '@/features/user/useAuth'
import { NotificationsContext } from './useNotifications'
import { brokerUrl } from './stompUrl'

// An ERROR frame is in practice a rejected CONNECT token, which no
// retry can fix — but the server sends one for any frame-handling
// failure, so a transient one gets a bounded number of chances before
// push is declared over for this session. Spaced at the reconnect
// policy's cap rather than its 1 s floor: a dead token must not be
// re-sent every second.
const ERROR_RETRY_LIMIT = 3
const ERROR_RETRY_DELAY_MS = 30_000

// the count stamped with the session that produced it, so a logout or
// re-login starts over without a reset write (the same derive-by-token
// rule AuthProvider's meCheck uses)
interface SessionCount {
  token: string
  count: number
}

export const NotificationProvider = () => {
  const { token } = useAuth()
  const accessToken = token?.accessToken ?? null
  const [sessionCount, setSessionCount] = useState<SessionCount | null>(null)
  const unreadCount =
    accessToken !== null && sessionCount?.token === accessToken
      ? sessionCount.count
      : 0

  // the session whose push channel gave up, stamped by the same
  // derive-by-token rule: a re-login is a new session and starts live.
  // An unset base URL needs no state — no socket is ever created, so it
  // is derived rather than written from the effect (which would cascade
  // a render).
  const [stoppedToken, setStoppedToken] = useState<string | null>(null)
  const pushStopped =
    accessToken !== null &&
    (serviceBaseUrls.notification === '' || stoppedToken === accessToken)

  useEffect(() => {
    if (!accessToken) return

    const base = serviceBaseUrls.notification
    if (base === '') {
      // softer than apiFetch's throw on purpose: a missing push channel
      // must not break every page the shell renders
      console.warn(
        'VITE_NOTIFICATION_SERVICE_URL is not set; notification push disabled',
      )
      // the bell reports this as a paused channel (derived above): no
      // socket will ever open, so it must not imply a live one
      return
    }

    // Created per effect run and deactivated in cleanup — safe under
    // StrictMode's double-invoked dev effects. Never hoist to module
    // scope.
    let errorRetries = 0
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let disposed = false

    const client = new Client({
      brokerURL: brokerUrl(base),
      // the handshake is open; this CONNECT header is what the server
      // authenticates ("Session mechanics", docs/notification-service.md)
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      // doc'd reconnect policy: 1 s doubling to a 30 s cap
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      // match the server's 10 s / 10 s heartbeats
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      // subscribing inside onConnect re-subscribes on every reconnect;
      // the body (NotificationDto on the server) is ignored until issue
      // #66's list/read UI — only the arrival is counted
      onConnect: () => {
        // a session that reached CONNECTED earns a fresh budget: the
        // next ERROR is a new failure, not a continuation of this one
        errorRetries = 0
        client.subscribe('/user/queue/notifications', () => {
          setSessionCount((prev) => ({
            token: accessToken,
            count: (prev?.token === accessToken ? prev.count : 0) + 1,
          }))
        })
      },
      // The server sends an ERROR frame for any frame-handling failure;
      // with this client's traffic (CONNECT + one fixed subscribe) that
      // is in practice a CONNECT auth failure (invalid/expired/revoked
      // token). The SPA has no token refresh, so an expired session
      // cannot recover on its own: retry a bounded number of times for
      // the transient case, then stop and let the bell report it rather
      // than leaving a frozen count looking live (PR #185 re-review).
      // Transport drops (no ERROR frame) still auto-reconnect with the
      // policy above.
      onStompError: () => {
        // the server closes the socket after an ERROR; stop the client's
        // own reconnect, which would re-send the rejected CONNECT within
        // a second
        void client.deactivate()
        if (disposed) return
        if (errorRetries >= ERROR_RETRY_LIMIT) {
          setStoppedToken(accessToken)
          return
        }
        errorRetries += 1
        retryTimer = setTimeout(() => {
          if (!disposed) void client.activate()
        }, ERROR_RETRY_DELAY_MS)
      },
    })
    client.activate()

    return () => {
      disposed = true
      clearTimeout(retryTimer)
      void client.deactivate()
    }
  }, [accessToken])

  const value = useMemo(
    () => ({ unreadCount, pushStopped }),
    [unreadCount, pushStopped],
  )

  return (
    <NotificationsContext.Provider value={value}>
      <Outlet />
    </NotificationsContext.Provider>
  )
}
