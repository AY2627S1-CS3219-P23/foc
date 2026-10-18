// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: tests for the order pages — the create form's client-side checks,
// the courier feed (own request disabled, accept conflict surfaced), and
// the requester's confirm-delivery action — against a spied orderApi, so
// they hold whatever the real order-service contract ends up being.
// Author review: [pending]

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import type { Order } from '@/features/order/types'
import { ApiError } from '@/lib/api/http'
import { CreateRequest } from '@/routes/create-request'
import { MyRequests } from '@/routes/my-requests'
import { OpenRequests } from '@/routes/open-requests'

vi.mock('@/features/order/useCurrentUserId', () => ({
  useCurrentUserId: () => 3,
}))

const base: Order = {
  id: 1,
  status: 'OPEN',
  description: '1x Iced Latte',
  reward: 3,
  supplierId: 's1',
  supplierName: 'CoffeeBean@Com3',
  pickupLocation: 'COM3',
  dropoffLocation: 'UTown Lobby',
  deadline: new Date(Date.now() + 3_600_000).toISOString(),
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  deliveredAt: null,
  requesterId: 3,
  requesterUsername: 'me',
  courierId: null,
  courierUsername: null,
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/my-requests/new" element={<CreateRequest />} />
        <Route path="/my-requests" element={<MyRequests />} />
        <Route path="/requests" element={<OpenRequests />} />
        <Route path="/deliveries/:id" element={<p>delivery page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const emptyPage = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 1,
}
const pageOf = (content: Order[]) => ({
  ...emptyPage,
  content,
  totalElements: content.length,
})

beforeEach(() => {
  vi.spyOn(orderApi, 'getBalance').mockResolvedValue({
    available: 10,
    reserved: 0,
    total: 10,
  })
  vi.spyOn(orderApi, 'listSupplierOptions').mockResolvedValue([
    { id: 's1', name: 'CoffeeBean@Com3', location: 'COM3' },
  ])
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('create request form', () => {
  it('blocks submit and explains when the reward exceeds the balance', async () => {
    const create = vi.spyOn(orderApi, 'createOrder')
    renderAt('/my-requests/new')
    await screen.findByRole('option', { name: 'CoffeeBean@Com3' })

    const reward = screen.getByLabelText('Credit reward')
    await userEvent.clear(reward)
    await userEvent.type(reward, '25')

    expect(await screen.findByText(/Insufficient credits/)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Submit Request' }),
    ).toBeDisabled()
    expect(create).not.toHaveBeenCalled()
  })

  it('asks for the required fields before calling the API', async () => {
    const create = vi.spyOn(orderApi, 'createOrder')
    renderAt('/my-requests/new')
    await screen.findByRole('option', { name: 'CoffeeBean@Com3' })

    await userEvent.click(
      screen.getByRole('button', { name: 'Submit Request' }),
    )

    expect(
      screen.getByText('Choose where to pick up from.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Describe what to collect.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('submits the order and goes to My Requests', async () => {
    vi.spyOn(orderApi, 'listMyOrders').mockResolvedValue(pageOf([]))
    const create = vi
      .spyOn(orderApi, 'createOrder')
      .mockResolvedValue({ ...base, id: 77 })
    renderAt('/my-requests/new')
    await screen.findByRole('option', { name: 'CoffeeBean@Com3' })

    await userEvent.selectOptions(
      screen.getByLabelText('Pickup supplier'),
      's1',
    )
    await userEvent.type(
      screen.getByLabelText('Drop-off location'),
      'UTown Lobby',
    )
    await userEvent.type(
      screen.getByLabelText('Item description'),
      '1x Iced Latte',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Submit Request' }),
    )

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        expect.objectContaining({
          supplierId: 's1',
          dropoffLocation: 'UTown Lobby',
          reward: 3,
        }),
      ),
    )
    expect(await screen.findByText(/Request #77 created/)).toBeInTheDocument()
  })
})

describe('open requests', () => {
  beforeEach(() => {
    vi.spyOn(orderApi, 'listMyOrders').mockResolvedValue(pageOf([]))
  })

  it("disables Accept on the student's own request", async () => {
    vi.spyOn(orderApi, 'listOpenOrders').mockResolvedValue(
      pageOf([
        { ...base, id: 5 },
        { ...base, id: 6, requesterId: 9, requesterUsername: 'other' },
      ]),
    )
    renderAt('/requests')

    expect(
      await screen.findByRole('button', { name: 'Your request' }),
    ).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Accept' })).toBeEnabled()
  })

  it('shows the conflict when another courier got there first, and refetches', async () => {
    const list = vi
      .spyOn(orderApi, 'listOpenOrders')
      .mockResolvedValue(
        pageOf([
          { ...base, id: 6, requesterId: 9, requesterUsername: 'other' },
        ]),
      )
    vi.spyOn(orderApi, 'acceptOrder').mockRejectedValue(
      new ApiError(409, { detail: 'This request was already taken.' }),
    )
    renderAt('/requests')

    await userEvent.click(await screen.findByRole('button', { name: 'Accept' }))

    expect(
      await screen.findByText('This request was already taken.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
  })

  it('steps back a page when the feed shrinks under the courier', async () => {
    const other = { ...base, id: 6, requesterId: 9, requesterUsername: 'other' }
    const list = vi
      .spyOn(orderApi, 'listOpenOrders')
      .mockResolvedValueOnce({ ...pageOf([other]), totalPages: 2 })
      // after page 2 is opened, it no longer exists: one page remains
      .mockResolvedValueOnce({ ...pageOf([]), totalPages: 1 })
      .mockResolvedValue({ ...pageOf([other]), totalPages: 1 })
    renderAt('/requests')
    await screen.findByRole('button', { name: 'Accept' })

    await userEvent.click(screen.getByRole('button', { name: /2/ }))

    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(0, expect.any(Number)),
    )
    expect(
      await screen.findByRole('button', { name: 'Accept' }),
    ).toBeInTheDocument()
  })

  it('opens the delivery page after a successful accept', async () => {
    vi.spyOn(orderApi, 'listOpenOrders').mockResolvedValue(
      pageOf([{ ...base, id: 6, requesterId: 9, requesterUsername: 'other' }]),
    )
    vi.spyOn(orderApi, 'acceptOrder').mockResolvedValue({
      ...base,
      id: 6,
      status: 'ACCEPTED',
    })
    renderAt('/requests')

    await userEvent.click(await screen.findByRole('button', { name: 'Accept' }))

    expect(await screen.findByText('delivery page')).toBeInTheDocument()
  })
})

describe('my requests', () => {
  it('confirms a delivered request', async () => {
    const delivered: Order = {
      ...base,
      status: 'DELIVERED',
      courierId: 8,
      courierUsername: 'fast_courier',
      deliveredAt: new Date().toISOString(),
    }
    vi.spyOn(orderApi, 'listMyOrders').mockResolvedValue(pageOf([delivered]))
    const confirm = vi
      .spyOn(orderApi, 'confirmDelivery')
      .mockResolvedValue({ ...delivered, status: 'COMPLETED' })
    renderAt('/my-requests')

    await userEvent.click(
      await screen.findByRole('button', { name: 'Confirm delivery' }),
    )

    await waitFor(() => expect(confirm).toHaveBeenCalledWith(1))
    expect(await screen.findByText(/Delivery confirmed/)).toBeInTheDocument()
  })

  it('asks the server for non-completed orders and pages through them', async () => {
    const list = vi.spyOn(orderApi, 'listMyOrders').mockResolvedValue({
      ...pageOf([{ ...base, id: 2 }]),
      totalPages: 3,
    })
    renderAt('/my-requests')

    await screen.findByText('Request #2')
    expect(list).toHaveBeenCalledWith(
      expect.objectContaining({ as: 'requester', scope: 'current', page: 0 }),
    )

    await userEvent.click(screen.getByRole('button', { name: /2/ }))
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(
        expect.objectContaining({ scope: 'current', page: 1 }),
      ),
    )
  })

  it('offers re-release on an expired request and not on an open one', async () => {
    vi.spyOn(orderApi, 'listMyOrders').mockResolvedValue(
      pageOf([
        { ...base, id: 2, status: 'EXPIRED' },
        { ...base, id: 3, status: 'OPEN' },
      ]),
    )
    renderAt('/my-requests')

    expect(
      await screen.findAllByRole('button', {
        name: 'Re-release as new request',
      }),
    ).toHaveLength(1)
    expect(
      screen.getByRole('button', { name: 'Cancel request' }),
    ).toBeInTheDocument()
  })
})
