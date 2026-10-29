// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-27, issue #233.
// Scope: tests for the order pages' push-triggered refetch
// (useRequestRefetch). The pages render inside a stub
// NotificationsContext whose pushed queue the tests feed directly — the
// provider's socket plumbing has its own suite; the unit here is the
// hook and each page's wiring. Fixtures and the spied orderApi follow
// orders.test.tsx; fake timers (house style: shouldAdvanceTime) drive
// the debounce window. orders.test.tsx keeps rendering these pages with
// no provider at all — that suite passing unchanged is the regression
// test for the hook's provider-less no-op.
// Author review: [pending]

import { act, render, screen, waitFor } from '@testing-library/react'
import { useEffect, useState, type ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { NotificationDto } from '@/features/notifications/types'
import {
  NotificationsContext,
  type NotificationsData,
} from '@/features/notifications/useNotifications'
import { REQUEST_REFETCH_DEBOUNCE_MS } from '@/features/notifications/useRequestRefetch'
import { orderApi } from '@/features/order/api'
import { ApiError } from '@/lib/api/http'
import type { RequestDetail, RequestSummary } from '@/features/order/types'
import { ActiveDelivery } from '@/routes/active-delivery'
import { MyRequests } from '@/routes/my-requests'
import { OpenRequests } from '@/routes/open-requests'
import { RequestHistory } from '@/routes/request-history'

const session = vi.hoisted(() => ({ userId: 3 as number | null }))
vi.mock('@/features/order/useCurrentUserId', () => ({
  useCurrentUserId: () => session.userId,
}))

const summary: RequestSummary = {
  id: 1,
  status: 'CREATED',
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
  courierId: null,
  supplierId: 1,
  acceptedAt: null,
  collectedAt: null,
  arrivedAt: null,
  ...over,
})

const pageOf = (content: RequestSummary[], over = {}) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: 1,
  ...over,
})

// A pushed STOMP frame as the backend sends it: requestId is a decimal
// string in the payload, never a number.
let frameSeq = 0
const frame = (
  requestId: number | string,
  eventType = 'request.accepted',
): NotificationDto => ({
  id: ++frameSeq,
  eventType,
  payload: { requestId: String(requestId), requesterId: '3' },
  occurredAt: new Date().toISOString(),
  read: false,
  createdAt: new Date().toISOString(),
})

// Stands in for NotificationProvider: same contract the pages rely on —
// the pushed array's reference changes once per appended frame.
let pushFrame: (dto: NotificationDto) => void
let reconnectPush: () => void
function PushProvider({ children }: { children: ReactNode }) {
  const [pushed, setPushed] = useState<NotificationDto[]>([])
  const [reconnects, setReconnects] = useState(0)
  useEffect(() => {
    pushFrame = (dto) => setPushed((prev) => [...prev, dto])
    reconnectPush = () => setReconnects((n) => n + 1)
  }, [])
  const value: NotificationsData = {
    unreadCount: 0,
    pushStopped: false,
    pushedNotifications: pushed,
    pushReconnects: reconnects,
    refreshUnreadCount: () => {},
    soundEnabled: false,
    setSoundEnabled: () => {},
  }
  return (
    <NotificationsContext.Provider value={value}>
      {children}
    </NotificationsContext.Provider>
  )
}

const push = (dto: NotificationDto) => act(() => pushFrame(dto))
const reconnect = () => act(() => reconnectPush())
// act-wrapped: the firing timer calls the page's refetch setState
const elapseDebounce = () =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(REQUEST_REFETCH_DEBOUNCE_MS)
  })

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PushProvider>
        <Routes>
          <Route path="/my-requests" element={<MyRequests />} />
          <Route path="/requests" element={<OpenRequests />} />
          <Route path="/history" element={<RequestHistory />} />
          <Route path="/deliveries/:id" element={<ActiveDelivery />} />
        </Routes>
      </PushProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  session.userId = 3
  vi.spyOn(orderApi, 'getUsername').mockImplementation(async (id) =>
    id === 3 ? 'carol' : 'dave',
  )
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('push-triggered refetch (issue #233)', () => {
  it('My Requests refetches when a pushed event matches a listed id', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValueOnce(pageOf([summary]))
      .mockResolvedValue(pageOf([{ ...summary, status: 'ACCEPTED' }]))
    // the now-in-progress card expands with its detail (courier dave)
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ACCEPTED', courierId: 7 }),
    )
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    expect(mine).toHaveBeenCalledTimes(1)

    // string payload id against the numeric row id — the wire format
    push(frame('1'))
    await elapseDebounce()

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
    await screen.findByText('dave')
  })

  it('ignores a pushed event for a request that is not shown', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    push(frame('999'))
    await elapseDebounce()
    await elapseDebounce()

    expect(mine).toHaveBeenCalledTimes(1)
  })

  it('coalesces a burst of matching events into one refetch', async () => {
    const other = { ...summary, id: 2, item: '1x Kopi' }
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary, other]))
    renderAt('/my-requests')

    await screen.findByText('1x Kopi')
    expect(mine).toHaveBeenCalledTimes(1)

    push(frame('1'))
    push(frame('2', 'request.cancelled'))
    await elapseDebounce()
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))

    // and nothing further fires once the window has passed
    await elapseDebounce()
    expect(mine).toHaveBeenCalledTimes(2)
  })

  it('Active Delivery reloads the request on a matching push', async () => {
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(detail({ status: 'ACCEPTED', courierId: 3 }))
      .mockResolvedValue(
        detail({
          status: 'COLLECTED',
          courierId: 3,
          collectedAt: new Date().toISOString(),
        }),
      )
    renderAt('/deliveries/1')

    await screen.findByRole('button', { name: 'Mark as picked up' })
    expect(get).toHaveBeenCalledTimes(1)

    push(frame('1', 'request.collected'))
    await elapseDebounce()

    await screen.findByText('Item collected — en route to drop-off')
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('Open Requests refreshes both lists on a push for a strip delivery', async () => {
    const delivery = { ...summary, id: 5, status: 'ACCEPTED' as const }
    const open = vi
      .spyOn(orderApi, 'listOpenRequests')
      .mockResolvedValue(pageOf([summary]))
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockImplementation(async ({ state }) =>
        state?.[0] === 'ACCEPTED' ? pageOf([delivery]) : pageOf([]),
      )
    renderAt('/requests')

    await screen.findByText(/#5 · 1x Iced Latte/)
    expect(open).toHaveBeenCalledTimes(1)
    const stripCalls = mine.mock.calls.length

    push(frame('5', 'request.collected'))
    await elapseDebounce()

    await waitFor(() => expect(open).toHaveBeenCalledTimes(2))
    expect(mine.mock.calls.length).toBeGreaterThan(stripCalls)
  })

  it('Request History refetches when a shown row is touched', async () => {
    const done = { ...summary, status: 'COMPLETED' as const }
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([done]))
    renderAt('/history')

    await screen.findByText('#1')
    expect(mine).toHaveBeenCalledTimes(1)

    push(frame('1', 'request.completed'))
    await elapseDebounce()

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })

  it('a pending debounce dies with the page', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    const { unmount } = renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    push(frame('1'))
    // the window is open — the assertion below is only meaningful if
    // there is a timer to clear (PR #264 review: a leaked timer's
    // setState is a silent no-op after unmount, so the call count alone
    // passes with the cleanup deleted)
    expect(vi.getTimerCount()).toBeGreaterThan(0)
    unmount()
    expect(vi.getTimerCount()).toBe(0)

    await elapseDebounce()
    await elapseDebounce()
    expect(mine).toHaveBeenCalledTimes(1)
  })
  it('ignores a frame with no eventType and a null payload', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    // a frame that parses but carries neither field: ignored, not a
    // throw inside the effect that would take the page down
    push({
      ...frame('1'),
      eventType: undefined,
      payload: null,
    } as unknown as NotificationDto)
    await elapseDebounce()

    expect(mine).toHaveBeenCalledTimes(1)
    // still rendered — the effect did not throw
    await screen.findByText('Waiting for a courier to accept.')
  })

  it('the PENDING poll does not re-read every expanded card detail', async () => {
    const pending = { ...summary, id: 2, status: 'PENDING' as const }
    const active = { ...summary, status: 'ACCEPTED' as const }
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([pending, active]))
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValue(detail({ status: 'ACCEPTED', courierId: 7 }))
    renderAt('/my-requests')

    await screen.findByText('dave')
    expect(get).toHaveBeenCalledTimes(1)

    // one poll window (PENDING_POLL_MS): the list refetches, the
    // expanded card's detail must not
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000)
    })
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('a push re-reads only the detail of the request it names', async () => {
    const other = { ...summary, id: 2, status: 'ACCEPTED' as const }
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([{ ...summary, status: 'ACCEPTED' as const }, other]),
    )
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockImplementation(async (id) =>
        detail({ id, status: 'ACCEPTED', courierId: 7 }),
      )
    renderAt('/my-requests')

    const callsFor = (id: number) =>
      get.mock.calls.filter(([arg]) => arg === id).length
    await waitFor(() => expect(callsFor(2)).toBe(1))
    expect(callsFor(1)).toBe(1)

    push(frame('1', 'request.collected'))
    await elapseDebounce()

    await waitFor(() => expect(callsFor(1)).toBe(2))
    expect(callsFor(2)).toBe(1)
  })

  it('Active Delivery keeps the delivery on screen when a background reload fails transiently', async () => {
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(detail({ status: 'ACCEPTED', courierId: 3 }))
      .mockRejectedValue(new ApiError(502, null))
    renderAt('/deliveries/1')

    await screen.findByRole('button', { name: 'Mark as picked up' })

    push(frame('1', 'request.collected'))
    await elapseDebounce()

    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    // the gateway blip said nothing about the request: the view stays
    expect(
      screen.getByRole('button', { name: 'Mark as picked up' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Could not load this request/)).toBeNull()
  })

  it('Active Delivery still gives up when a background reload is refused', async () => {
    vi.spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(detail({ status: 'ACCEPTED', courierId: 3 }))
      .mockRejectedValue(new ApiError(404, null))
    renderAt('/deliveries/1')

    await screen.findByRole('button', { name: 'Mark as picked up' })

    push(frame('1', 'request.cancelled'))
    await elapseDebounce()

    // a definitive answer: better than leaving a stale action button
    await screen.findByText(/Request failed \(404\)|does not exist/)
    expect(
      screen.queryByRole('button', { name: 'Mark as picked up' }),
    ).toBeNull()
  })

  it('My Requests drops the error banner once a refetch succeeds', async () => {
    const page = pageOf([summary])
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValueOnce(page)
      // fetch's own network failure, which errorMessage renders as the
      // page's fallback text
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(page)
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')

    push(frame('1'))
    await elapseDebounce()
    await screen.findByText('Could not load your requests. Try again.')

    push(frame('1', 'request.collected'))
    await elapseDebounce()

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(3))
    await waitFor(() =>
      expect(
        screen.queryByText('Could not load your requests. Try again.'),
      ).toBeNull(),
    )
  })

  it('a push-channel reconnect refetches what the page shows', async () => {
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    await screen.findByText('Waiting for a courier to accept.')
    expect(mine).toHaveBeenCalledTimes(1)

    // frames sent while the socket was down reached nobody, so every
    // shown row is suspect
    reconnect()
    await elapseDebounce()

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })
})
