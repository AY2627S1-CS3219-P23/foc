// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: chat-service's REST reads (a request's chats, a history page, a
// message's image) and its STOMP broker URL.
// 2026-10-31: markRead, when the pop-up opens (C19).
// Author review: Ko-Khan, pending pull request review.

import { serviceBaseUrls } from '@/lib/api/config'
import { apiFetch } from '@/lib/api/http'
import { brokerUrl } from '@/features/notifications/stompUrl'
import type { ChatMessage, ChatView } from './types'

// a party: the open chat they are in (or none); an admin: every chat the
// request has had, newest first
export function listChats(requestId: number) {
  return apiFetch<ChatView[]>('chat', `/chats?requestId=${requestId}`)
}

// newest first; pass the oldest sentAt shown to page back
export function listMessages(chatId: string, before?: string, size = 50) {
  const search = new URLSearchParams({ size: String(size) })
  if (before) search.set('before', before)
  return apiFetch<ChatMessage[]>(
    'chat',
    `/chats/${chatId}/messages?${search.toString()}`,
  )
}

// the pop-up is open: the caller's unread count starts over (C19)
export function markRead(chatId: string) {
  return apiFetch<void>('chat', `/chats/${chatId}/read`, { method: 'POST' })
}

// An <img> can't send the bearer token, so the image is fetched here and
// shown from an object URL; the caller revokes it.
export async function fetchImageUrl(
  chatId: string,
  messageId: string,
  token: string,
): Promise<string> {
  const res = await fetch(
    `${serviceBaseUrls.chat}/chats/${chatId}/messages/${messageId}/image`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!res.ok) throw new Error(`Image failed to load (${res.status})`)
  return URL.createObjectURL(await res.blob())
}

// same SockJS raw-WebSocket transport as notification-service's
export function chatBrokerUrl(): string {
  return brokerUrl(serviceBaseUrls.chat)
}
