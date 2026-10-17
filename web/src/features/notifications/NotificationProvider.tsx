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
// 2026-10-17, Claude Code (Fable 5), issue #66: the badge count seeds
// from GET /notifications/me/unread-count on login and re-seeds on
// every (re)connect — a reconnect may have missed frames — and after
// the Center's writes via refreshUnreadCount (author decision via
// options Q&A: the server count is authoritative; pushes increment on
// top of the seeded baseline). The pushed frame's body is now parsed
// and exposed as latestNotification so the open Center can prepend the
// row (author decision via options Q&A). Count sync failures are
// non-fatal, like the missing-URL case: a broken badge must not break
// every page the shell renders.

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Outlet } from 'react-router'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { serviceBaseUrls } from '@/lib/api/config'
import { useAuth } from '@/features/user/useAuth'
import { getUnreadCount } from './api'
import { NotificationsContext } from './useNotifications'
import { brokerUrl } from './stompUrl'
import type { NotificationDto } from './types'

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

  // the last pushed frame, stamped like the count so a re-login never
  // shows the previous session's notification
  const [pushed, setPushed] = useState<{
    token: string
    dto: NotificationDto
  } | null>(null)
  const latestNotification =
    accessToken !== null && pushed?.token === accessToken ? pushed.dto : null

  // Seeds/re-syncs the badge from the REST count (issue #66). Replaces
  // the count wholesale: the server stores a notification before
  // pushing it, so its count is authoritative over local increments.
  // Failure keeps the current count — same softer-than-apiFetch stance
  // as the missing-URL branch below.
  const refreshUnreadCount = useCallback(() => {
    if (!accessToken) return
    getUnreadCount()
      .then((body) => {
        // treat a wrong-shaped body like a failed sync: a NaN badge is
        // worse than a briefly stale one
        if (typeof body?.count === 'number' && Number.isFinite(body.count)) {
          setSessionCount({ token: accessToken, count: body.count })
        } else {
          console.warn(
            'unread-count sync returned no count; keeping the current badge',
          )
        }
      })
      .catch((err) => {
        console.warn('unread-count sync failed; keeping the current badge', err)
      })
  }, [accessToken])

  // seed on login (REST works even when the push socket cannot)
  useEffect(() => {
    refreshUnreadCount()
  }, [refreshUnreadCount])

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
      // subscribing inside onConnect re-subscribes on every reconnect
      onConnect: () => {
        // a session that reached CONNECTED earns a fresh budget: the
        // next ERROR is a new failure, not a continuation of this one
        errorRetries = 0
        // a reconnect may have missed frames; the REST count catches
        // the badge up (issue #66)
        refreshUnreadCount()
        client.subscribe('/user/queue/notifications', (message) => {
          setSessionCount((prev) => ({
            token: accessToken,
            count: (prev?.token === accessToken ? prev.count : 0) + 1,
          }))
          // the body is the same NotificationDto the REST list returns;
          // the open Center prepends it. The count above never depends
          // on the body parsing (a malformed frame still counts).
          try {
            setPushed({
              token: accessToken,
              dto: JSON.parse(message.body) as NotificationDto,
            })
          } catch {
            console.warn('unparseable notification push frame ignored')
          }
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
  }, [accessToken, refreshUnreadCount])

  const value = useMemo(
    () => ({
      unreadCount,
      pushStopped,
      latestNotification,
      refreshUnreadCount,
    }),
    [unreadCount, pushStopped, latestNotification, refreshUnreadCount],
  )

  return (
    <NotificationsContext.Provider value={value}>
      <Outlet />
    </NotificationsContext.Provider>
  )
}
