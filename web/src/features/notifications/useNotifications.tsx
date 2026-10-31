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
// 2026-10-17, Claude Code (Opus 5), PR #199 review (@codex): the single
// latestNotification became a queue — two frames delivered in one
// WebSocket message batch into a single render, and only the last would
// have survived a replaceable field.
// 2026-10-28, Claude Code (Opus 5), PR #264 review (@Sinnez1), issue
// #233: pushReconnects exposed — frames delivered while the socket was
// down reach nobody, so a page that only reacts to pushes would stay
// stale after the reconnect. useRequestRefetch treats a bump as a
// refetch trigger.
// 2026-10-18, Claude Code (Fable 5), notification sound (follow-up to
// issue #66, no issue yet): soundEnabled/setSoundEnabled added so the
// provider's chime and both toggle surfaces (Center header, Profile
// preferences — author decision via options Q&A) share one source of
// truth.
// 2026-10-30, Claude Code (Fable 5), issue #266: pendingReadIds +
// markReadPending — the optimistic mark-read moved up from the Center
// so it survives the navigation that unmounts the page (#263 review's
// fire-and-forget nit).

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
  // the notifications pushed over STOMP this session, oldest first and
  // capped: the open Notification Center prepends the ones that arrive
  // while it is mounted. A queue, not just the newest — a burst
  // delivered inside one render must not lose all but its last frame
  // (PR #199 review). The reference only changes when a frame arrives.
  pushedNotifications: NotificationDto[]
  // how many times this session's push channel has *re*connected: a
  // session's first connect is not counted (a page's own mount fetch
  // already covers it), so a bump always means frames may have been
  // missed while the socket was down. Pages use it to refetch what
  // they show (issue #233, PR #264 review).
  pushReconnects: number
  // Rows optimistically marked read via markReadPending whose PATCH the
  // server has not been seen to miss: the Center overlays these onto
  // fetched rows, so a Back that outruns the PATCH does not flash the
  // row unread (issue #266). Successful ids stay for the session;
  // failed ones leave, so the truth wins the next fetch.
  pendingReadIds: ReadonlySet<number>
  // Mark one row read without waiting — for click-to-navigate, where
  // the page unmounts before the PATCH answers. Adds the id to
  // pendingReadIds up front and re-syncs the badge on success; kept
  // here because the provider outlives the route (issue #266).
  markReadPending: (id: number) => void
  // re-fetch the unread count from the REST API — the Center calls it
  // after a successful mark-read/read-all, keeping the server
  // authoritative over the badge
  refreshUnreadCount: () => void
  // whether a live push plays the chime. A device preference, not an
  // account one (sound is a property of the machine you sit at):
  // localStorage-backed, default on. Shared here so the Center header
  // toggle and the Profile preferences switch always agree.
  soundEnabled: boolean
  setSoundEnabled: (enabled: boolean) => void
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
