// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: chat-service's response and frame shapes.
// 2026-10-31: the unread count and the receiver's unread frame (C16-C19).
// Author review: Ko-Khan, pending pull request review.

export type ChatStatus = 'OPEN' | 'CLOSED'

export interface ChatView {
  id: string
  requestId: number
  requesterId: number
  courierId: number
  status: ChatStatus
  openedAt: string
  closedAt: string | null
  // the caller's unread messages in it (C19); 0 for admins
  unreadCount: number
}

export interface ChatMessage {
  id: string
  chatId: string
  senderId: number
  text: string | null
  hasImage: boolean
  sentAt: string
}

// what /topic/chats/{chatId} delivers
export type ChatFrame =
  | { type: 'MESSAGE'; chatId: string; message: ChatMessage }
  | { type: 'CLOSED'; chatId: string; message: null }

// what the receiver's own queue (/user/queue/chat) delivers per message:
// the chat's new unread count, and whether this one made it unread (C17)
export interface UnreadFrame {
  chatId: string
  requestId: number
  unreadCount: number
  first: boolean
  senderId: number
  preview: string | null // the text's first 80 characters
  hasImage: boolean
}

// the order states a request's chat is open in (decision C3)
export const CHAT_OPEN_STATUSES = ['ACCEPTED', 'COLLECTED', 'ARRIVED'] as const
