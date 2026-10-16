// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: context + hook exposing the session's unread notification
// count (badge-only by the author's scope decision; the Notification
// Center and read-state REST are issue #66). Mirrors useAuth's shape.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1):
// pushStopped added, so the bell can report a push channel that gave
// up instead of showing its last count as live.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from 'react'

export interface NotificationsData {
  // notifications pushed over the STOMP session since login/connect;
  // session-local on purpose — persistent read state is issue #66
  unreadCount: number
  // true once this session's push channel has given up (a rejected
  // CONNECT token that outlived the retry budget, or no configured
  // notification URL): unreadCount is frozen from here on and the bell
  // must not present it as live. Only a re-login restores push — the
  // SPA has no token refresh (PR #185 re-review)
  pushStopped: boolean
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
