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
// Reviewed by: Leong Wei Zhi (via pull request).

import { useEffect, useMemo, useState } from 'react'
import { Outlet } from 'react-router'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { serviceBaseUrls } from '@/lib/api/config'
import { useAuth } from '@/features/user/useAuth'
import { NotificationsContext } from './useNotifications'
import { brokerUrl } from './stompUrl'

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

  useEffect(() => {
    if (!accessToken) return

    const base = serviceBaseUrls.notification
    if (base === '') {
      // softer than apiFetch's throw on purpose: a missing push channel
      // must not break every page the shell renders
      console.warn(
        'VITE_NOTIFICATION_SERVICE_URL is not set; notification push disabled',
      )
      return
    }

    // Created per effect run and deactivated in cleanup — safe under
    // StrictMode's double-invoked dev effects. Never hoist to module
    // scope.
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
        client.subscribe('/user/queue/notifications', () => {
          setSessionCount((prev) => ({
            token: accessToken,
            count: (prev?.token === accessToken ? prev.count : 0) + 1,
          }))
        })
      },
      // the server sends an ERROR frame for any frame-handling failure;
      // with this client's traffic (CONNECT + one fixed subscribe) that
      // is in practice a CONNECT auth failure (invalid/expired/revoked
      // token), which a retry can never fix — stop instead of hammering.
      // Recovery is a re-login: the token change re-runs this effect.
      // Transport drops (no ERROR frame) still auto-reconnect with the
      // policy above.
      onStompError: () => {
        void client.deactivate()
      },
    })
    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [accessToken])

  const value = useMemo(() => ({ unreadCount }), [unreadCount])

  return (
    <NotificationsContext.Provider value={value}>
      <Outlet />
    </NotificationsContext.Provider>
  )
}
