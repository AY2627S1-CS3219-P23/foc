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
// 2026-10-20: My Requests lists active states, History finished ones, each
// as one state list.
// 2026-10-20: My Requests refetches every 2 seconds while a request is
// PENDING, and stops once none is.
// Author review: [pending]
// 2026-10-29, Claude Code (Opus 5.5), issue #241: fixtures carry
// autoCompleteAt; My Requests lists DISPUTED; "Confirm delivery" asks
// first and going back changes nothing; the countdown comes from the list
// item; "Report a problem" shows only while order-service would accept it
// and disappears when the window closes; the dispute dialog's checks, its
// /dispute body and a 409 shown in it; a DISPUTED card's category; the
// confirm dialog's link closing with the window; History's RESOLVED rows
// and their amounts.
// Author review: Ryan Ang, pending pull request review.

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
          params.state?.includes('COLLECTED')
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
    expect(mine.mock.calls.map(([p]) => p.state?.join()).sort()).toEqual([
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
  it('lists the active states by default and filters by one state per chip', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    await screen.findByText('Request #1')
    expect(mine).toHaveBeenLastCalledWith({
      role: 'requester',
      state: [
        'PENDING',
        'CREATED',
        'ACCEPTED',
        'COLLECTED',
        'ARRIVED',
        'DISPUTED',
      ],
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
        expect.objectContaining({ state: ['ARRIVED'], page: 0 }),
      ),
    )
  })

  it('offers only the active states as filters, after All active', async () => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([summary]))
    renderAt('/my-requests')

    const group = await screen.findByRole('group', { name: 'Filter by state' })
    expect(
      within(group)
        .getAllByRole('button')
        .map((b) => b.textContent),
    ).toEqual([
      'All active',
      'Open',
      'Accepted',
      'Collected',
      'Arrived',
      'Disputed',
    ])
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

  it('refetches every 2 seconds while a request is PENDING, then stops', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const mine = vi
        .spyOn(orderApi, 'listMyRequests')
        .mockResolvedValueOnce(pageOf([{ ...summary, status: 'PENDING' }]))
        .mockResolvedValue(pageOf([summary]))
      renderAt('/my-requests')

      await screen.findByText(/Reserving your credits/)
      expect(mine).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(2000)
      await screen.findByText('Waiting for a courier to accept.')
      expect(mine).toHaveBeenCalledTimes(2)

      // nothing is PENDING any more, so no further refetch
      await vi.advanceTimersByTimeAsync(6000)
      expect(mine).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
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

  it("confirms an ARRIVED request only from the dialog, showing the courier and order-service's auto-complete time", async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([arrived(40 * 60_000)]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    const complete = vi
      .spyOn(orderApi, 'complete')
      .mockResolvedValue({ message: 'Request 1 completed' })
    renderAt('/my-requests')

    expect(await screen.findByText('dave')).toBeInTheDocument()
    expect(screen.getByText(/Auto-completes in 40 min/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm delivery' }))

    const dialog = within(
      screen.getByRole('dialog', { name: 'Confirm delivery?' }),
    )
    expect(dialog.getByText('3 Credits')).toBeInTheDocument()
    expect(dialog.getByText(/can’t be undone/)).toBeInTheDocument()
    expect(complete).not.toHaveBeenCalled()
    await user.click(dialog.getByRole('button', { name: 'Confirm delivery' }))

    expect(complete).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/Delivery confirmed/)).toBeInTheDocument()
  })

  it('changes nothing when the confirm dialog is cancelled', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([arrived(40 * 60_000)]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    const complete = vi.spyOn(orderApi, 'complete')
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Confirm delivery' }),
    )
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Cancel',
      }),
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(complete).not.toHaveBeenCalled()
    expect(mine).toHaveBeenCalledTimes(1)
  })

  it('offers "Report a problem" only while order-service would accept it', async () => {
    const past = new Date(Date.now() - 60_000).toISOString()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([
        arrived(40 * 60_000, 1),
        // past autoCompleteAt, the sweep not run yet
        arrived(-60_000, 2),
        // no autoCompleteAt known
        { ...summary, id: 3, status: 'ARRIVED' },
        { ...summary, id: 4, status: 'COLLECTED', deadline: past },
        { ...summary, id: 5, status: 'COLLECTED' },
        { ...summary, id: 6, status: 'EXPIRED' },
      ]),
    )
    vi.spyOn(orderApi, 'getRequest').mockImplementation(async (id) =>
      detail({ id, courierId: 9 }),
    )
    renderAt('/my-requests')

    const card = async (id: number) => {
      const heading = await screen.findByText(`Request #${id}`)
      return within(heading.closest('li') as HTMLElement)
    }
    const report = { name: 'Report a problem' }
    expect((await card(1)).getByRole('button', report)).toBeInTheDocument()
    expect((await card(2)).queryByRole('button', report)).toBeNull()
    expect(
      (await card(2)).getByText(/Completing automatically…/),
    ).toBeInTheDocument()
    expect((await card(2)).queryByText(/Auto-completes in/)).toBeNull()
    expect((await card(3)).queryByRole('button', report)).toBeNull()
    expect((await card(4)).getByRole('button', report)).toBeInTheDocument()
    expect((await card(5)).queryByRole('button', report)).toBeNull()
    expect((await card(6)).queryByRole('button', report)).toBeNull()
  })

  it('hides "Report a problem" once the window closes', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
        pageOf([arrived(3000)]),
      )
      vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
        detail({ status: 'ARRIVED', courierId: 9 }),
      )
      renderAt('/my-requests')

      await screen.findByRole('button', { name: 'Report a problem' })
      await vi.advanceTimersByTimeAsync(4000)

      expect(
        screen.queryByRole('button', { name: 'Report a problem' }),
      ).not.toBeInTheDocument()
      expect(screen.getByText(/Completing automatically…/)).toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('reports a problem: category required, a note required for Other, then /dispute', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([arrived(40 * 60_000)]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    const dispute = vi
      .spyOn(orderApi, 'dispute')
      .mockResolvedValue({ message: 'Request 1 disputed' })
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Report a problem' }),
    )
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))
    expect(dialog.getByText('Choose what went wrong.')).toBeInTheDocument()

    await user.click(dialog.getByLabelText('Other'))
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))
    expect(dialog.getByText('Describe the problem.')).toBeInTheDocument()

    await user.type(dialog.getByLabelText(/Details/), 'Courier left early')
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))

    expect(dispute).toHaveBeenCalledTimes(1)
    expect(dispute).toHaveBeenCalledWith(1, {
      category: 'OTHER',
      note: 'Courier left early',
    })
    expect(
      await screen.findByText(/Problem reported for request #1/),
    ).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })

  it('refuses a note over 500 characters before calling the API', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([arrived(40 * 60_000)]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    const dispute = vi.spyOn(orderApi, 'dispute')
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    const dialog = within(screen.getByRole('dialog'))
    await user.click(dialog.getByLabelText('Damaged item'))
    await user.click(dialog.getByLabelText(/Details/))
    await user.paste('x'.repeat(501))
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))

    expect(
      dialog.getByText('Keep the note to 500 characters.'),
    ).toBeInTheDocument()
    expect(dispute).not.toHaveBeenCalled()
  })

  it("shows order-service's 409 in the dialog", async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([arrived(40 * 60_000)]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    vi.spyOn(orderApi, 'dispute').mockRejectedValue(
      new ApiError(409, {
        status: 409,
        detail: 'The dispute window closed at 2026-10-29T07:00:00Z',
      }),
    )
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    const dialog = within(screen.getByRole('dialog'))
    await user.click(dialog.getByLabelText('Wrong item'))
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))

    expect(await dialog.findByRole('alert')).toHaveTextContent(
      'The dispute window closed at 2026-10-29T07:00:00Z',
    )
  })

  it('names the held reward in the singular for a 1-credit request', async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([{ ...arrived(40 * 60_000), creditAmount: 1 }]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    expect(
      within(screen.getByRole('dialog')).getByText(
        /The reward of 1 credit stays held .* how it is divided\./i,
      ),
    ).toBeInTheDocument()
  })

  it('cancelling the report dialog without submitting does not refetch', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([arrived(40 * 60_000)]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Cancel',
      }),
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mine).toHaveBeenCalledTimes(1)
  })

  it('refetches after closing the report dialog on a rejected submit', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([arrived(40 * 60_000)]))
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    vi.spyOn(orderApi, 'dispute').mockRejectedValue(
      new ApiError(409, { status: 409, detail: 'Request is not ARRIVED' }),
    )
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Report a problem' }),
    )
    const dialog = within(screen.getByRole('dialog'))
    await user.click(dialog.getByLabelText('Wrong item'))
    await user.click(dialog.getByRole('button', { name: 'Report problem' }))
    await dialog.findByRole('alert')
    await user.click(dialog.getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(mine).toHaveBeenCalledTimes(2))
  })

  it('reads a fresh clock when the countdown starts', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const user = userEvent.setup({
        advanceTimers: vi.advanceTimersByTime.bind(vi),
      })
      const mine = vi
        .spyOn(orderApi, 'listMyRequests')
        .mockResolvedValue(pageOf([]))
      renderAt('/my-requests')
      await waitFor(() => expect(mine).toHaveBeenCalledTimes(1))

      // the page sits with nothing to count down; the window opens and
      // closes meanwhile
      await vi.advanceTimersByTimeAsync(10 * 60_000)
      mine.mockResolvedValue(pageOf([arrived(-60_000)]))
      vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
        detail({ status: 'ARRIVED', courierId: 9 }),
      )
      await user.click(
        within(
          screen.getByRole('group', { name: 'Filter by state' }),
        ).getByRole('button', { name: 'Arrived' }),
      )

      expect(
        await screen.findByText(/Completing automatically…/),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Report a problem' }),
      ).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it("drops the confirm dialog's report link once the window closes", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const user = userEvent.setup({
        advanceTimers: vi.advanceTimersByTime.bind(vi),
      })
      vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
        pageOf([arrived(3000)]),
      )
      vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
        detail({ status: 'ARRIVED', courierId: 9 }),
      )
      renderAt('/my-requests')

      await user.click(
        await screen.findByRole('button', { name: 'Confirm delivery' }),
      )
      const dialog = within(
        screen.getByRole('dialog', { name: 'Confirm delivery?' }),
      )
      expect(
        dialog.getByRole('button', { name: 'Report a problem' }),
      ).toBeInTheDocument()

      await vi.advanceTimersByTimeAsync(4000)

      expect(
        dialog.queryByRole('button', { name: 'Report a problem' }),
      ).not.toBeInTheDocument()
      // confirming is still offered
      expect(
        dialog.getByRole('button', { name: 'Confirm delivery' }),
      ).toBeEnabled()
    } finally {
      vi.useRealTimers()
    }
  })

  it("opens the report dialog from the confirm dialog's link", async () => {
    const user = userEvent.setup()
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([arrived(40 * 60_000)]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'ARRIVED', courierId: 9 }),
    )
    const complete = vi.spyOn(orderApi, 'complete')
    renderAt('/my-requests')

    await user.click(
      await screen.findByRole('button', { name: 'Confirm delivery' }),
    )
    await user.click(
      within(
        screen.getByRole('dialog', { name: 'Confirm delivery?' }),
      ).getByRole('button', { name: 'Report a problem' }),
    )

    expect(
      screen.queryByRole('dialog', { name: 'Confirm delivery?' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole('dialog', { name: 'Report a problem' }),
    ).toBeInTheDocument()
    expect(complete).not.toHaveBeenCalled()
  })

  it('shows a DISPUTED request with its category and no actions', async () => {
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([
        {
          ...summary,
          status: 'DISPUTED',
          dispute: {
            category: 'WRONG_ITEM',
            disputeNote: 'Got a mocha',
            raisedAt: new Date().toISOString(),
            autoEscalated: false,
            resolvedAt: null,
            resolvedBy: null,
            courierAmount: null,
            requesterAmount: null,
            resolutionNote: null,
          },
        },
      ]),
    )
    vi.spyOn(orderApi, 'getRequest').mockResolvedValue(
      detail({ status: 'DISPUTED', courierId: 9 }),
    )
    renderAt('/my-requests')

    expect(
      await screen.findByText(/PROBLEM REPORTED — WRONG ITEM/),
    ).toBeInTheDocument()
    expect(screen.getByText('“Got a mocha”')).toBeInTheDocument()
    expect(
      screen.queryAllByRole('button', { name: /Confirm|Report|Cancel/ }),
    ).toHaveLength(0)
  })
})

// An ARRIVED list item that auto-completes `inMs` from now.
function arrived(inMs: number, id = 1): RequestSummary {
  return {
    ...summary,
    id,
    status: 'ARRIVED',
    autoCompleteAt: new Date(Date.now() + inMs).toISOString(),
  }
}

describe('request history', () => {
  it('starts on all finished requests and switches sides', async () => {
    const user = userEvent.setup()
    const mine = vi
      .spyOn(orderApi, 'listMyRequests')
      .mockResolvedValue(pageOf([{ ...summary, status: 'COMPLETED' }]))
    renderAt('/history')

    // the table and the mobile cards are both in the page under jsdom
    expect((await screen.findAllByText('−3 credits')).length).toBeGreaterThan(0)
    expect(mine).toHaveBeenLastCalledWith({
      role: 'requester',
      state: ['COMPLETED', 'CANCELLED', 'EXPIRED', 'REJECTED', 'RESOLVED'],
      page: 0,
      size: 10,
    })

    await user.click(screen.getByRole('tab', { name: /As Courier/ }))

    expect((await screen.findAllByText('+3 credits')).length).toBeGreaterThan(0)
    expect(mine).toHaveBeenLastCalledWith(
      expect.objectContaining({
        role: 'courier',
        state: ['COMPLETED', 'EXPIRED', 'RESOLVED'],
      }),
    )
    // a courier's request can't be cancelled or rejected
    expect(
      within(screen.getByRole('group', { name: 'Filter by state' }))
        .getAllByRole('button')
        .map((b) => b.textContent),
    ).toEqual(['All finished', 'Completed', 'Expired', 'Resolved'])
  })

  it("shows a resolved request's courier share, labelled by outcome", async () => {
    const user = userEvent.setup()
    const resolved = (id: number, courierAmount: number): RequestSummary => ({
      ...summary,
      id,
      status: 'RESOLVED',
      closeReason: 'DISPUTE_RESOLVED',
      dispute: {
        category: 'WRONG_ITEM',
        disputeNote: null,
        raisedAt: new Date().toISOString(),
        autoEscalated: false,
        resolvedAt: new Date().toISOString(),
        resolvedBy: 99,
        courierAmount,
        requesterAmount: 3 - courierAmount,
        resolutionNote: null,
      },
    })
    vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(
      pageOf([resolved(1, 1), resolved(2, 0), resolved(3, 3)]),
    )
    renderAt('/history')

    // the table is re-rendered after a tab switch, so look it up each time
    const row = (id: number) =>
      within(
        within(screen.getByRole('table'))
          .getByText(`#${id}`)
          .closest('tr') as HTMLElement,
      )
    await screen.findByRole('table')
    expect(row(1).getByText('−1 credits')).toBeInTheDocument()
    expect(row(1).getByText('Resolved: split')).toBeInTheDocument()
    expect(row(2).getByText('0 credits')).toBeInTheDocument()
    expect(row(2).getByText('Resolved: refunded')).toBeInTheDocument()
    expect(row(3).getByText('−3 credits')).toBeInTheDocument()
    expect(row(3).getByText('Resolved: paid')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /As Courier/ }))
    expect((await screen.findAllByText('+1 credits')).length).toBeGreaterThan(0)
    expect(row(2).getByText('0 credits')).toBeInTheDocument()
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
