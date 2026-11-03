// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the receiver's chat notifications, through chat-service only
// (C16): while signed in the app holds one chat-service socket on the
// user's own queue (/user/queue/chat). Each frame updates that request's
// Chat button badge (C19); the frame for the message that made a chat
// unread (C17) shows a toast with the sender's name and a text preview
// (C18), plus a browser notification when the tab is in the background
// (C20). The single chat pop-up lives here too, so a toast can open it
// and a frame for the chat being read is not announced. Reconnect
// timings copy NotificationProvider's.
// 2026-10-31: the pop-up and toasts are stamped with the session that
// opened them, so a sign-out (an expired token's 401 signs the user out)
// closes them instead of leaving a pop-up showing the 401.
// Author review: Ko-Khan, pending pull request review.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Outlet } from 'react-router'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { serviceBaseUrls } from '@/lib/api/config'
import { orderApi } from '@/features/order/api'
import { useAuth } from '@/features/user/useAuth'
import { chatBrokerUrl } from './api'
import { ChatPopup } from './ChatPopup'
import type { UnreadFrame } from './types'
import {
  ChatNotificationsContext,
  type ChatNotifications,
} from './useChatNotifications'

// how long a toast stays up unless clicked or closed
const TOAST_MS = 8_000

interface Toast {
  token: string
  key: string
  requestId: number
  sender: string
  body: string
}

interface OpenChat {
  token: string
  requestId: number
  readOnly: boolean
}

function bodyOf(frame: UnreadFrame): string {
  return frame.preview ?? (frame.hasImage ? 'Sent an image' : 'New message')
}

// Asks once, from inside a click (browsers only show the prompt for a
// user gesture): opening a chat is the first moment a user shows they
// chat at all.
function askNotificationPermission() {
  if (
    typeof Notification !== 'undefined' &&
    Notification.permission === 'default'
  ) {
    void Notification.requestPermission().catch(() => {})
  }
}

export const ChatNotificationsProvider = () => {
  const { token } = useAuth()
  const accessToken = token?.accessToken ?? null

  // stamped with the session, like NotificationProvider's count, so a
  // re-login never shows the previous account's badges
  const [unreadState, setUnreadState] = useState<{
    token: string
    counts: Record<number, number>
  } | null>(null)
  const unread = useMemo(
    () =>
      accessToken !== null && unreadState?.token === accessToken
        ? unreadState.counts
        : {},
    [accessToken, unreadState],
  )

  // both stamped with the session, like the counts: another session's
  // (or none, after a sign-out) never shows
  const [openState, setOpen] = useState<OpenChat | null>(null)
  const open = openState?.token === accessToken ? openState : null
  const [toastState, setToasts] = useState<Toast[]>([])
  const toasts = toastState.filter((t) => t.token === accessToken)

  // the pop-up's request, readable from the socket callback
  const openRef = useRef<OpenChat | null>(null)
  useEffect(() => {
    openRef.current = open
  }, [open])

  const setUnread = useCallback(
    (requestId: number, count: number) => {
      if (!accessToken) return
      setUnreadState((prev) => ({
        token: accessToken,
        counts: {
          ...(prev?.token === accessToken ? prev.counts : {}),
          [requestId]: count,
        },
      }))
    },
    [accessToken],
  )

  const dismiss = useCallback((key: string) => {
    setToasts((prev) => prev.filter((t) => t.key !== key))
  }, [])

  const openChat = useCallback(
    (requestId: number, readOnly: boolean) => {
      if (!accessToken) return
      askNotificationPermission()
      setOpen({ token: accessToken, requestId, readOnly })
      // the chat is being read now: its toasts have done their job
      setToasts((prev) => prev.filter((t) => t.requestId !== requestId))
    },
    [accessToken],
  )

  const notify = useCallback(
    async (frame: UnreadFrame) => {
      if (!accessToken) return
      const sessionToken = accessToken
      const sender = await orderApi
        .getUsername(frame.senderId)
        .catch(() => `User #${frame.senderId}`)
      const body = bodyOf(frame)
      const key = `${frame.chatId}:${Date.now()}`
      setToasts((prev) => [
        ...prev.filter((t) => t.requestId !== frame.requestId),
        { token: sessionToken, key, requestId: frame.requestId, sender, body },
      ])
      window.setTimeout(() => dismiss(key), TOAST_MS)
      // the toast can't be seen from another tab or app: a system
      // notification can (C20). One per chat (tag), replacing the last.
      if (
        document.visibilityState !== 'visible' &&
        typeof Notification !== 'undefined' &&
        Notification.permission === 'granted'
      ) {
        const shown = new Notification(
          `${sender} — Request #${frame.requestId}`,
          {
            body,
            tag: `chat-${frame.chatId}`,
          },
        )
        shown.onclick = () => {
          window.focus()
          openChat(frame.requestId, false)
          shown.close()
        }
      }
    },
    [accessToken, dismiss, openChat],
  )

  useEffect(() => {
    if (!accessToken || !serviceBaseUrls.chat) return
    const client = new Client({
      brokerURL: chatBrokerUrl(),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        client.subscribe('/user/queue/chat', (message) => {
          let frame: UnreadFrame
          try {
            frame = JSON.parse(message.body) as UnreadFrame
          } catch {
            console.warn('unparseable chat unread frame ignored')
            return
          }
          // the pop-up showing this chat marks it read itself; the
          // message is in front of the user, so nothing to announce
          if (openRef.current?.requestId === frame.requestId) return
          setUnread(frame.requestId, frame.unreadCount)
          if (frame.first) void notify(frame)
        })
      },
      // a refused CONNECT (expired or revoked token): no retry loop; the
      // badges just stop updating until the next sign-in
      onStompError: () => {
        void client.deactivate()
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [accessToken, setUnread, notify])

  const value = useMemo<ChatNotifications>(
    () => ({ unread, setUnread, openChat }),
    [unread, setUnread, openChat],
  )

  return (
    <ChatNotificationsContext.Provider value={value}>
      <Outlet />
      {open && (
        <ChatPopup
          requestId={open.requestId}
          readOnly={open.readOnly}
          onClose={() => setOpen(null)}
        />
      )}
      {toasts.length > 0 && (
        <div
          className="fixed right-4 bottom-20 z-40 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2 sm:bottom-4"
          aria-live="polite"
        >
          {toasts.map((t) => (
            <div
              key={t.key}
              role="status"
              className="flex items-start gap-2 rounded-lg border border-gray-200 bg-white p-3 shadow-lg"
            >
              <button
                type="button"
                onClick={() => openChat(t.requestId, false)}
                className="min-w-0 flex-1 text-left"
              >
                <p className="text-sm font-semibold text-gray-900">
                  {t.sender}{' '}
                  <span className="font-normal text-gray-500">
                    · Request #{t.requestId}
                  </span>
                </p>
                <p className="truncate text-sm text-gray-700">{t.body}</p>
              </button>
              <button
                type="button"
                onClick={() => dismiss(t.key)}
                aria-label="Dismiss"
                className="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}
    </ChatNotificationsContext.Provider>
  )
}
