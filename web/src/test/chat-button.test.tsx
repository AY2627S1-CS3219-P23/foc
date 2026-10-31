// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the Chat button's unread badge (C19): seeded from GET /chats,
// shown from the shared unread counts, absent for admins' read-only
// button; a click opens the shared pop-up.
// Author review: Ko-Khan, pending pull request review.

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ChatButton } from '@/features/chat/ChatButton'
import {
  ChatNotificationsContext,
  type ChatNotifications,
} from '@/features/chat/useChatNotifications'

const listChats = vi.fn()
vi.mock('@/features/chat/api', () => ({
  listChats: (requestId: number) => listChats(requestId),
}))

function withContext(value: ChatNotifications, readOnly = false) {
  return render(
    <ChatNotificationsContext.Provider value={value}>
      <ChatButton requestId={5} readOnly={readOnly} />
    </ChatNotificationsContext.Provider>,
  )
}

describe('Chat button', () => {
  it('seeds and shows the unread count, and opens the shared pop-up', async () => {
    listChats.mockResolvedValue([{ id: 'c1', unreadCount: 3 }])
    const setUnread = vi.fn()
    const openChat = vi.fn()

    withContext({ unread: { 5: 3 }, setUnread, openChat })

    await waitFor(() => expect(setUnread).toHaveBeenCalledWith(5, 3))
    const button = screen.getByRole('button', {
      name: 'Chat, 3 unread messages',
    })
    expect(button).toHaveTextContent('3')
    await userEvent.click(button)
    expect(openChat).toHaveBeenCalledWith(5, false)
  })

  it('shows no badge when nothing is unread', () => {
    listChats.mockResolvedValue([])
    withContext({ unread: {}, setUnread: vi.fn(), openChat: vi.fn() })

    expect(screen.getByRole('button', { name: 'Chat' })).toBeInTheDocument()
  })

  it("gives an admin's read-only button no badge and no lookup", async () => {
    listChats.mockClear()
    const openChat = vi.fn()

    withContext({ unread: { 5: 4 }, setUnread: vi.fn(), openChat }, true)

    const button = screen.getByRole('button', { name: 'View chat' })
    expect(button).not.toHaveTextContent('4')
    expect(listChats).not.toHaveBeenCalled()
    await userEvent.click(button)
    expect(openChat).toHaveBeenCalledWith(5, true)
  })
})
