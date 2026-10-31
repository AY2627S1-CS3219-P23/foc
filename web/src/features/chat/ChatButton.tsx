// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the button on an order that opens its chat pop-up (C14).
// 2026-10-31: an unread-count badge on it (C19), seeded from GET /chats
// and kept live by ChatNotificationsProvider, whose single pop-up it now
// opens.
// Author review: Ko-Khan, pending pull request review.

import { useEffect, useState } from 'react'
import { listChats } from './api'
import { ChatPopup } from './ChatPopup'
import { useChatNotifications } from './useChatNotifications'

interface ChatButtonProps {
  requestId: number
  // admins: the history, read-only (C13); they have no unread count
  readOnly?: boolean
  className?: string
}

const OUTLINE =
  'relative rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50'

export function ChatButton({
  requestId,
  readOnly = false,
  className = OUTLINE,
}: ChatButtonProps) {
  const notifications = useChatNotifications()
  // only used without the provider (a page rendered on its own)
  const [localOpen, setLocalOpen] = useState(false)

  // seeds the badge: the open chat's unread count when the card appears;
  // the provider's live frames take it from there
  const setUnread = notifications?.setUnread
  useEffect(() => {
    if (readOnly || !setUnread) return
    let cancelled = false
    listChats(requestId)
      .then((chats) => {
        if (!cancelled) setUnread(requestId, chats[0]?.unreadCount ?? 0)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [requestId, readOnly, setUnread])

  const unread = readOnly ? 0 : (notifications?.unread[requestId] ?? 0)

  return (
    <>
      <button
        type="button"
        onClick={() =>
          notifications
            ? notifications.openChat(requestId, readOnly)
            : setLocalOpen(true)
        }
        className={className}
        aria-label={
          unread > 0
            ? `Chat, ${unread} unread message${unread === 1 ? '' : 's'}`
            : undefined
        }
      >
        {readOnly ? 'View chat' : 'Chat'}
        {unread > 0 && (
          <span className="absolute -top-2 -right-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-xs font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {!notifications && localOpen && (
        <ChatPopup
          requestId={requestId}
          readOnly={readOnly}
          onClose={() => setLocalOpen(false)}
        />
      )}
    </>
  )
}
