// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: context + hook exposing the session's unread notification
// count (badge-only by the author's scope decision; the Notification
// Center and read-state REST are issue #66). Mirrors useAuth's shape.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1):
// pushStopped added, so the bell can report a push channel that gave
// up instead of showing its last count as live.
// 2026-10-17, Claude Code (Fable 5), issue #66: the count is no longer
// session-local — it seeds from GET /notifications/me/unread-count and
// re-syncs via refreshUnreadCount after the Center's writes (author
// decision via options Q&A: refetch, not local adjustment). The last
// pushed notification body is exposed so the open Center can prepend
// it live (author decision via options Q&A).
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from 'react'
import type { NotificationDto } from './types'

export interface NotificationsData {
  // unread notifications for this session's user: seeded from the REST
  // unread-count endpoint on login/reconnect, bumped per STOMP push,
  // re-synced after the Center's writes (issue #66)
  unreadCount: number
  // true once this session's push channel has given up (a rejected
  // CONNECT token that outlived the retry budget, or no configured
  // notification URL): unreadCount is frozen from here on and the bell
  // must not present it as live. Only a re-login restores push — the
  // SPA has no token refresh (PR #185 re-review)
  pushStopped: boolean
  // the most recent notification pushed over STOMP this session (null
  // until one arrives): the open Notification Center prepends it
  latestNotification: NotificationDto | null
  // re-fetch the unread count from the REST API — the Center calls it
  // after a successful mark-read/read-all, keeping the server
  // authoritative over the badge
  refreshUnreadCount: () => void
}

export const NotificationsContext = createContext<NotificationsData | null>(
  null,
)

export const useNotifications = () => {
  const context = useContext(NotificationsContext)

  if (context === null) {
    throw new Error(
      'useNotifications must be used within a NotificationProvider',
    )
  }

  return context
}
