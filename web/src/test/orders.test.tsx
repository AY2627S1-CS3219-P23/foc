// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: tests for the order pages against a spied orderApi.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: rewritten for
// order-service's real contract — the create body, the open feed's
// deadline sort and the backend's 409 shown on accept, the courier's
// strip from one call per in-progress state, the delivery page's steps
// reloading the request (and a drop leaving the page), My Requests' state
// filter, PENDING / REJECTED messages, cancel and confirm, History's
// default Completed filter, and usernames from user-service.
// 2026-10-19: My Requests' filter offers only the lifecycle states.
// Author review: [pending]

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import type { RequestDetail, RequestSummary } from '@/features/order/types'
import { ApiError } from '@/lib/api/http'
import { CreateRequest } from '@/routes/create-request'
import { MyRequests } from '@/routes/my-requests'
import { ActiveDelivery } from '@/routes/active-delivery'
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

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/my-requests/new" element={<CreateRequest />} />
        <Route path="/my-requests" element={<MyRequests />} />
        <Route path="/requests" element={<OpenRequests />} />
        <Route path="/history" element={<RequestHistory />} />
        <Route path="/deliveries/:id" element={<ActiveDelivery />} />
      </Routes>
    </MemoryRouter>,
  )
}

const pageOf = (content: RequestSummary[], over = {}) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: 1,
  ...over,
})

beforeEach(() => {
  session.userId = 3
  vi.spyOn(orderApi, 'getBalance').mockResolvedValue({
    available: 10,
    reserved: 0,
    total: 10,
  })
  vi.spyOn(orderApi, 'listSupplierOptions').mockResolvedValue([
    { id: '7', name: 'CoffeeBean@Com3', location: 'COM3' },
  ])
  vi.spyOn(orderApi, 'getUsername').mockImplementation(async (id) =>
    id === 3 ? 'carol' : 'dave',
  )
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('create request form', () => {
  it('blocks submit and explains when the reward exceeds the balance', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(orderApi, 'createRequest')
    renderAt('/my-requests/new')

    const reward = screen.getByLabelText('Credit reward')
    await user.clear(reward)
    await user.type(reward, '20')

    expect(await screen.findByText(/Insufficient credits/)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Submit Request' }),
    ).toBeDisabled()
    expect(create).not.toHaveBeenCalled()
  })

  it('asks for the required fields before calling the API', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(orderApi, 'createRequest')
    renderAt('/my-requests/new')

    await user.click(screen.getByRole('button', { name: 'Submit Request' }))

    expect(
      screen.getByText('Choose where to pick up from.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Describe what to collect.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it("posts order-service's /create body and goes to My Requests", async () => {
    const user = userEvent.setup()
    const create = vi
      .spyOn(orderApi, 'createRequest')
      .mockResolvedValue({ id: 12, message: 'Request 12 created' })
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([]))
    renderAt('/my-requests/new')

    await screen.findByRole('option', { name: 'CoffeeBean@Com3' })
    await user.selectOptions(screen.getByLabelText('Pickup supplier'), '7')
    await user.type(screen.getByLabelText('Drop-off location'), 'UTown Lobby')
    await user.type(screen.getByLabelText('Item'), '1x Iced Latte')
    await user.click(screen.getByRole('button', { name: 'Submit Request' }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({
        supplierId: 7,
        supplierName: 'CoffeeBean@Com3',
        dropoffLocation: 'UTown Lobby',
        item: '1x Iced Latte',
        creditAmount: 3,
      }),
    )
    expect(
      await screen.findByText(/Request #12 created — reserving 3 Credits/),
    ).toBeInTheDocument()
  })

  it("shows order-service's 400 as-is", async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'createRequest').mockRejectedValue(
      new ApiError(400, { detail: 'Missing required fields: supplierName' }),
    )
    renderAt('/my-requests/new')

    await screen.findByRole('option', { name: 'CoffeeBean@Com3' })
    await user.selectOptions(screen.getByLabelText('Pickup supplier'), '7')
    await user.type(screen.getByLabelText('Drop-off location'), 'COM3')
    await user.type(screen.getByLabelText('Item'), 'Tea')
    await user.click(screen.getByRole('button', { name: 'Submit Request' }))

    expect(
      await screen.findByText('Missing required fields: supplierName'),
    ).toBeInTheDocument()
  })
})

describe('open requests', () => {
  beforeEach(() => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([]))
  })

  it('asks for the open feed nearest deadline first', async () => {
    const open = vi
      .spyOn(orderApi, 'listOpenRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/requests')

    expect(await screen.findByText('1x Iced Latte')).toBeInTheDocument()
    expect(open).toHaveBeenCalledWith({ page: 0, size: 9, sort: 'deadline' })
  })

  it("shows the backend's 409 when an accept is refused, and refetches", async () => {
    const user = userEvent.setup()
    const open = vi
      .spyOn(orderApi, 'listOpenRequests')
      .mockResolvedValue(pageOf([summary]))
    vi.spyOn(orderApi, 'accept').mockRejectedValue(
      new ApiError(409, {
        detail: 'A requester cannot accept their own request',
      }),
    )
    renderAt('/requests')

    await user.click(await screen.findByRole('button', { name: 'Accept' }))

    expect(
      await screen.findByText('A requester cannot accept their own request'),
    ).toBeInTheDocument()
    await waitFor(() => expect(open).toHaveBeenCalledTimes(2))
  })

  it('opens the delivery page after a successful accept', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listOpenRequests').mockResolvedValue(pageOf([summary]))
    vi.spyOn(orderApi, 'accept').mockResolvedValue({
      message: 'Request 1 accepted',
    })
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ACCEPTED', courierId: 3, requesterId: 9 }),
    )
    renderAt('/requests')

    await user.click(await screen.findByRole('button', { name: 'Accept' }))

    expect(
      await screen.findByRole('heading', {
        name: 'Active Delivery — Request #1',
      }),
    ).toBeInTheDocument()
  })

  it("builds the courier's strip from one call per in-progress state", async () => {
    vi.spyOn(orderApi, 'listOpenRequests').mockResolvedValue(pageOf([]))
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockImplementation(async (params) =>
        pageOf(
          params.state === 'COLLECTED'
            ? [
                {
                  ...summary,
                  id: 4,
                  status: 'COLLECTED',
                  item: 'Printed notes',
                },
              ]
            : [],
        ),
      )
    renderAt('/requests')

    const strip = await screen.findByRole('region', {
      name: 'Your active deliveries',
    })
    expect(within(strip).getByText(/Printed notes/)).toBeInTheDocument()
    expect(mine.mock.calls.map(([p]) => p.state).sort()).toEqual([
      'ACCEPTED',
      'ARRIVED',
      'COLLECTED',
    ])
  })

  it('still shows the feed when the strip fails to load', async () => {
    vi.spyOn(orderApi, 'listOpenRequests').mockResolvedValue(pageOf([summary]))
    vi.spyOn(orderApi, 'listMyRequests').mockRejectedValue(new Error('down'))
    renderAt('/requests')

    expect(await screen.findByText('1x Iced Latte')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('steps back a page when the feed shrinks under the courier', async () => {
    const user = userEvent.setup()
    const open = vi
      .spyOn(orderApi, 'listOpenRequests')
      .mockResolvedValueOnce(
        pageOf([summary], { totalPages: 2, totalElements: 10 }),
      )
      .mockResolvedValueOnce(
        pageOf([], { page: 1, totalPages: 1, totalElements: 1 }),
      )
      .mockResolvedValue(pageOf([summary]))
    renderAt('/requests')

    await user.click(await screen.findByRole('button', { name: 'Page 2' }))

    await waitFor(() =>
      expect(open).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 0 }),
      ),
    )
  })
})

describe('active delivery', () => {
  it('collects, then shows the reloaded request', async () => {
    const user = userEvent.setup()
    const get = vi
      .spyOn(orderApi, 'getRequest')
      .mockResolvedValueOnce(
        detail({ status: 'ACCEPTED', courierId: 3, requesterId: 9 }),
      )
      .mockResolvedValue(
        detail({ status: 'COLLECTED', courierId: 3, requesterId: 9 }),
      )
    vi.spyOn(orderApi, 'collect').mockResolvedValue({
      message: 'Request 1 collected',
    })
    renderAt('/deliveries/1')

    expect(await screen.findByText('dave')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Mark as picked up' }))

    expect(
      await screen.findByRole('button', {
        name: 'Mark as arrived at drop-off',
      }),
    ).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('leaves the page after a drop, since the request is no longer readable', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ACCEPTED', courierId: 3, requesterId: 9 }),
    )
    const drop = vi
      .spyOn(orderApi, 'drop')
      .mockResolvedValue({ message: 'Request 1 dropped' })
    vi.spyOn(orderApi, 'listOpenRequests').mockResolvedValue(pageOf([]))
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([]))
    renderAt('/deliveries/1')

    await user.click(
      await screen.findByRole('button', { name: 'Cancel delivery' }),
    )
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Cancel delivery',
      }),
    )

    expect(drop).toHaveBeenCalledWith(1)
    expect(
      await screen.findByRole('heading', { name: 'Available Errands' }),
    ).toBeInTheDocument()
  })

  it("shows the backend's 404 when the request is not the caller's", async () => {
    vi.spyOn(orderApi, 'getRequest').mockRejectedValue(
      new ApiError(404, { detail: 'Request not found' }),
    )
    renderAt('/deliveries/1')

    expect(await screen.findByText('Request not found')).toBeInTheDocument()
  })

  it('drops the previous request when the URL changes to another delivery', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'getRequest').mockImplementation(async (id) =>
      id === 1
        ? detail({ status: 'ACCEPTED', courierId: 3, requesterId: 9 })
        : detail({
            id: 2,
            item: 'Second',
            status: 'COLLECTED',
            courierId: 3,
            requesterId: 9,
          }),
    )
    function GoToTwo() {
      const navigate = useNavigate()
      return (
        <button type="button" onClick={() => navigate('/deliveries/2')}>
          go
        </button>
      )
    }
    render(
      <MemoryRouter initialEntries={['/deliveries/1']}>
        <GoToTwo />
        <Routes>
          <Route path="/deliveries/:id" element={<ActiveDelivery />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('1x Iced Latte')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'go' }))

    expect(await screen.findByText('Second')).toBeInTheDocument()
    expect(screen.queryByText('1x Iced Latte')).not.toBeInTheDocument()
  })
})

describe('my requests', () => {
  it('lists every state by default and filters by one state per chip', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    await screen.findByText('Request #1')
    expect(mine).toHaveBeenLastCalledWith({
      role: 'requester',
      state: undefined,
      page: 0,
      size: 10,
    })

    await user.click(
      within(screen.getByRole('group', { name: 'Filter by state' })).getByRole(
        'button',
        {
          name: 'Arrived',
        },
      ),
    )
    await waitFor(() =>
      expect(mine).toHaveBeenLastCalledWith(
        expect.objectContaining({ state: 'ARRIVED', page: 0 }),
      ),
    )
  })

  it('offers only the lifecycle states as filters, after All', async () => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    const group = await screen.findByRole('group', { name: 'Filter by state' })
    expect(
      within(group)
        .getAllByRole('button')
        .map((b) => b.textContent),
    ).toEqual(['All', 'Open', 'Accepted', 'Collected', 'Arrived', 'Completed'])
  })

  it('explains a PENDING and a REJECTED request', async () => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([
        { ...summary, id: 1, status: 'PENDING' },
        { ...summary, id: 2, status: 'REJECTED' },
      ]),
    )
    renderAt('/my-requests')

    expect(
      await screen.findByText(/Reserving your credits/),
    ).toBeInTheDocument()
    expect(screen.getByText(/NOT ENOUGH AVAILABLE CREDITS/)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Cancel request' }),
    ).not.toBeInTheDocument()
  })

  it('cancels a CREATED request and reloads the list', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    const cancel = vi
      .spyOn(orderApi, 'cancel')
      .mockResolvedValue({ message: 'Request 1 cancelled' })
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Cancel request' }),
    )
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Cancel request',
      }),
    )

    expect(cancel).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/Request #1 cancelled/)).toBeInTheDocument()
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })

  it('confirms an ARRIVED request, showing the courier and the auto-complete time', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([{ ...summary, status: 'ARRIVED' }]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({
        status: 'ARRIVED',
        courierId: 9,
        arrivedAt: new Date().toISOString(),
      }),
    )
    const complete = vi
      .spyOn(orderApi, 'complete')
      .mockResolvedValue({ message: 'Request 1 completed' })
    renderAt('/my-requests')

    expect(await screen.findByText('dave')).toBeInTheDocument()
    expect(screen.getByText(/Auto-completes at/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm delivery' }))

    expect(complete).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/Delivery confirmed/)).toBeInTheDocument()
  })

  it('offers no dispute or re-release (the backend has neither)', async () => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([
        { ...summary, id: 1, status: 'EXPIRED' },
        { ...summary, id: 2, status: 'ARRIVED' },
      ]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    renderAt('/my-requests')

    await screen.findByText('Request #1')
    expect(
      screen.queryByRole('button', { name: /Dispute/ }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /Re-release/ }),
    ).not.toBeInTheDocument()
  })
})

describe('request history', () => {
  it('starts on Completed and switches sides', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([{ ...summary, status: 'COMPLETED' }]))
    renderAt('/history')

    // the table and the mobile cards are both in the page under jsdom
    expect((await screen.findAllByText('−3 credits')).length).toBeGreaterThan(0)
    expect(mine).toHaveBeenLastCalledWith({
      role: 'requester',
      state: 'COMPLETED',
      page: 0,
      size: 10,
    })

    await user.click(screen.getByRole('tab', { name: /As Courier/ }))

    expect((await screen.findAllByText('+3 credits')).length).toBeGreaterThan(0)
    expect(mine).toHaveBeenLastCalledWith(
      expect.objectContaining({ role: 'courier', state: 'COMPLETED' }),
    )
  })

  it('steps back a page when the requested page no longer exists', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValueOnce(
        pageOf([{ ...summary, status: 'COMPLETED' }], {
          totalPages: 2,
          totalElements: 11,
        }),
      )
      .mockResolvedValueOnce(
        pageOf([], { page: 1, totalPages: 1, totalElements: 1 }),
      )
      .mockResolvedValue(pageOf([{ ...summary, status: 'COMPLETED' }]))
    renderAt('/history')

    await user.click(await screen.findByRole('button', { name: 'Page 2' }))

    await waitFor(() =>
      expect(mine).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 0 }),
      ),
    )
  })
})
