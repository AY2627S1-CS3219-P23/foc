// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-04.
// Scope: tests for the order pages' timed refetch (usePollWhileActive):
// every ORDER_POLL_MS while a shown request is in progress, paused while
// the tab is hidden, a catch-up refetch when it is shown again, and on
// My Requests a detail re-read only for cards whose status moved.
// Rendered without a NotificationProvider, so no push can trigger a
// refetch; fake timers (house style: shouldAdvanceTime) drive the poll.
// Author review: Ko-Khan, pending pull request review.

import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import type { RequestDetail, RequestSummary } from '@/features/order/types'
import { ORDER_POLL_MS } from '@/features/order/usePollWhileActive'
import { ActiveDelivery } from '@/routes/active-delivery'
import { MyRequests } from '@/routes/my-requests'

const session = vi.hoisted(() => ({ userId: 7 as number | null }))
vi.mock('@/features/order/useCurrentUserId', () => ({
  useCurrentUserId: () => session.userId,
}))

const summary: RequestSummary = {
  id: 1,
  status: 'ACCEPTED',
  supplierName: 'CoffeeBean@Com3',
  dropoffLocation: 'UTown Lobby',
  item: '1x Iced Latte',
  creditAmount: 3,
  deadline: new Date(Date.now() + 3_600_000).toISOString(),
  createdAt: new Date().toISOString(),
  autoCompleteAt: null,
}

const detail = (over: Partial<RequestDetail> = {}): RequestDetail => ({
  ...summary,
  requesterId: 3,
  courierId: 7,
  supplierId: 1,
  acceptedAt: new Date().toISOString(),
  collectedAt: null,
  arrivedAt: null,
  ...over,
})

const pageOf = (content: RequestSummary[]) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: 1,
})

const elapse = (ms: number) =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })

let visibility: DocumentVisibilityState = 'visible'
const setVisibility = (next: DocumentVisibilityState) =>
  act(() => {
    visibility = next
    document.dispatchEvent(new Event('visibilitychange'))
  })

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/my-requests" element={<MyRequests />} />
        <Route path="/deliveries/:id" element={<ActiveDelivery />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  session.userId = 7
  visibility = 'visible'
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(
    () => visibility,
  )
  vi.spyOn(orderApi, 'getUsername').mockImplementation(async (id) =>
    id === 3 ? 'carol' : 'dave',
  )
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('My Requests timed refetch', () => {
  it('refetches every few seconds while a request is in progress', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValueOnce(pageOf([{ ...summary, status: 'CREATED' }]))
      .mockResolvedValue(pageOf([summary]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(detail())
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    expect(mine).toHaveBeenCalledTimes(1)

    // the courier accepted meanwhile; no push, the poll picks it up
    await elapse(ORDER_POLL_MS)
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
    await screen.findByText('dave')
  })

  it("re-reads a card's detail only when its status moved", async () => {
    vi.spyOn(orderApi, 'listMyRequests')
      .mockResolvedValueOnce(pageOf([summary]))
      .mockResolvedValueOnce(pageOf([summary]))
      .mockResolvedValue(pageOf([{ ...summary, status: 'COLLECTED' }]))
    const get = vi.spyOn(orderApi, 'getRequest').mockResolvedValue(detail())
    renderAt('/my-requests')

    await screen.findByText('dave')
    expect(get).toHaveBeenCalledTimes(1)

    // same status: the list is refetched, the detail is not
    await elapse(ORDER_POLL_MS)
    expect(get).toHaveBeenCalledTimes(1)

    // ACCEPTED -> COLLECTED: that card re-reads its detail
    await elapse(ORDER_POLL_MS)
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
  })

  it('does not poll with nothing in progress', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([]))
    renderAt('/my-requests')

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(1))
    await elapse(ORDER_POLL_MS * 3)

    expect(mine).toHaveBeenCalledTimes(1)
  })

  it('pauses while the tab is hidden and catches up when it is shown', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(detail())
    renderAt('/my-requests')

    await screen.findByText('dave')
    setVisibility('hidden')
    await elapse(ORDER_POLL_MS * 3)
    expect(mine).toHaveBeenCalledTimes(1)

    setVisibility('visible')
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })
})

describe('Active Delivery timed refetch', () => {
  it('reloads while in progress and stops once the request is terminal', async () => {
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(detail())
      .mockResolvedValue(detail({ status: 'COMPLETED' }))
    renderAt('/deliveries/1')

    await waitFor(() => expect(get).toHaveBeenCalledTimes(1))
    await screen.findByText('carol')

    // the requester confirmed delivery meanwhile
    await elapse(ORDER_POLL_MS)
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))

    // COMPLETED: nothing left to watch
    await elapse(ORDER_POLL_MS * 3)
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('keeps the view when a timed reload fails', async () => {
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(detail())
      .mockRejectedValue(new Error('network down'))
    renderAt('/deliveries/1')

    await screen.findByText('carol')
    await elapse(ORDER_POLL_MS)
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))

    expect(screen.getByText('carol')).toBeInTheDocument()
    expect(
      screen.queryByText('Could not load this request. Try again.'),
    ).not.toBeInTheDocument()
  })
})
