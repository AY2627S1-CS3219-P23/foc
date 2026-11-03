// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the context ChatNotificationsProvider shares: each request's
// unread count for the Chat button badge (C19) and the one chat pop-up.
// Author review: Ko-Khan, pending pull request review.

import { createContext, useContext } from 'react'

export interface ChatNotifications {
  // the caller's unread messages per request id, as far as known
  unread: Record<number, number>
  setUnread: (requestId: number, count: number) => void
  // opens the request's chat pop-up (only one at a time)
  openChat: (requestId: number, readOnly: boolean) => void
}

export const ChatNotificationsContext = createContext<ChatNotifications | null>(
  null,
)

// null outside the provider (e.g. a page rendered on its own in a test):
// the Chat button then falls back to its own pop-up, without a badge
export function useChatNotifications(): ChatNotifications | null {
  return useContext(ChatNotificationsContext)
}
