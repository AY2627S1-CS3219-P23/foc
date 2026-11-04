// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-04, Order F8.1.
// Scope: Open Requests' "Too far (>1 km)" state (usePickupProximity): a
// request whose pickup is more than 1 km from a held delivery's gets the
// disabled button, a near one keeps Accept, and with no delivery held, a
// failed supplier load or an unknown pickup nothing is marked (the
// server's check decides). Supplier coordinates come from a mocked
// listSuppliers, fixtures follow orders.test.tsx.
// Author review: Ko-Khan, pending pull request review.

import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import type { RequestSummary } from '@/features/order/types'
import { listSuppliers } from '@/features/supplier/api'
import type { Supplier } from '@/features/supplier/types'
import { OpenRequests } from '@/routes/open-requests'

vi.mock('@/features/supplier/api', () => ({ listSuppliers: vi.fn() }))

const LAT = 1.2966
const LNG = 103.7764

// 0.0089 deg of latitude is about 0.99 km, 0.0091 deg about 1.01 km
const suppliers: Supplier[] = [
  supplier('1', 'Held Cafe', LAT),
  supplier('2', 'Near Cafe', LAT + 0.0089),
  supplier('3', 'Far Cafe', LAT + 0.0091),
]

function supplier(id: string, name: string, latitude: number): Supplier {
  return {
    id,
    name,
    location: name,
    latitude,
    longitude: LNG,
    categories: [],
    openingTime: '08:00',
    closingTime: '20:00',
    description: '',
    bookmarked: false,
  }
}

const request = (
  id: number,
  supplierId: number,
  supplierName: string,
  over: Partial<RequestSummary> = {},
): RequestSummary => ({
  id,
  status: 'CREATED',
  supplierId,
  supplierName,
  dropoffLocation: 'UTown Lobby',
  item: `Item ${id}`,
  creditAmount: 3,
  deadline: new Date(Date.now() + 3_600_000).toISOString(),
  createdAt: new Date().toISOString(),
  autoCompleteAt: null,
  ...over,
})

const pageOf = <T,>(content: T[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
})

const held = request(10, 1, 'Held Cafe', { status: 'ACCEPTED' })

function stubFeed(open: RequestSummary[], mine: RequestSummary[]) {
  vi.spyOn(orderApi, 'listOpenRequests').mockResolvedValue(pageOf(open))
  vi.spyOn(orderApi, 'listMyRequests').mockImplementation(async (params) =>
    pageOf(mine.filter((r) => params.state?.includes(r.status))),
  )
}

function renderFeed() {
  return render(
    <MemoryRouter initialEntries={['/requests']}>
      <Routes>
        <Route path="/requests" element={<OpenRequests />} />
      </Routes>
    </MemoryRouter>,
  )
}

// the card holding the given item
const card = async (item: string) =>
  (await screen.findByText(item)).closest('li') as HTMLElement

beforeEach(() => {
  vi.mocked(listSuppliers).mockResolvedValue(pageOf(suppliers))
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.mocked(listSuppliers).mockReset()
})

describe('Open Requests too-far marking (Order F8.1)', () => {
  it('marks a request more than 1 km from a held pickup and keeps Accept for a near one', async () => {
    stubFeed([request(1, 2, 'Near Cafe'), request(2, 3, 'Far Cafe')], [held])
    renderFeed()

    const far = await card('Item 2')
    expect(
      await within(far).findByRole('button', { name: 'Too far (>1 km)' }),
    ).toBeDisabled()
    const near = await card('Item 1')
    expect(within(near).getByRole('button', { name: 'Accept' })).toBeEnabled()
  })

  it('marks nothing, and loads no suppliers, while no delivery is held', async () => {
    stubFeed([request(2, 3, 'Far Cafe')], [])
    renderFeed()

    const far = await card('Item 2')
    expect(within(far).getByRole('button', { name: 'Accept' })).toBeEnabled()
    expect(listSuppliers).not.toHaveBeenCalled()
  })

  it('marks nothing when the suppliers cannot be loaded', async () => {
    vi.mocked(listSuppliers).mockRejectedValue(new Error('down'))
    stubFeed([request(2, 3, 'Far Cafe')], [held])
    renderFeed()

    const far = await card('Item 2')
    await vi.waitFor(() => expect(listSuppliers).toHaveBeenCalled())
    expect(within(far).getByRole('button', { name: 'Accept' })).toBeEnabled()
  })

  it('leaves a pickup it has no coordinates for to the server', async () => {
    stubFeed([request(2, 99, 'Unlisted Cafe')], [held])
    renderFeed()

    const unknown = await card('Item 2')
    await vi.waitFor(() => expect(listSuppliers).toHaveBeenCalled())
    expect(
      within(unknown).getByRole('button', { name: 'Accept' }),
    ).toBeEnabled()
  })
})
