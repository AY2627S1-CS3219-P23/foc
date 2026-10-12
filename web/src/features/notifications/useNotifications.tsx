// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: context + hook exposing the session's unread notification
// count (badge-only by the author's scope decision; the Notification
// Center and read-state REST are issue #66). Mirrors useAuth's shape.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from 'react'

export interface NotificationsData {
  // notifications pushed over the STOMP session since login/connect;
  // session-local on purpose — persistent read state is issue #66
  unreadCount: number
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
