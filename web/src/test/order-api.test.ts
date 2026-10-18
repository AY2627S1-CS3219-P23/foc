// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-18.
// Scope: orderApi's supplier picker loads every page of suppliers, not
// just the first (PR review, LeongWZ).
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: the calls against
// order-service's real contract — paths, query parameters and bodies;
// its {"message"} error bodies surfaced as the ApiError's message; the id
// read from /create's message; usernames from user-service, cached.
// Author review: [pending]

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { listSuppliers } from '@/features/supplier/api'
import { orderApi } from '@/features/order/api'
import type { Supplier } from '@/features/supplier/types'
import { ApiError } from '@/lib/api/http'

vi.mock('@/features/supplier/api', () => ({ listSuppliers: vi.fn() }))
// the service base URLs are read once at import, as http.test.ts mocks them
vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://gw/api/user',
    supplier: 'http://gw/api/supplier',
    order: 'http://gw/api/order',
    credit: 'http://gw/api/credit',
    notification: 'http://gw/api/notification',
  },
}))

const supplier = (id: string): Supplier => ({
  id,
  name: `Supplier ${id}`,
  location: 'Somewhere',
  latitude: 0,
  longitude: 0,
  categories: [],
  openingTime: '08:00',
  closingTime: '20:00',
  description: '',
})

describe('orderApi.listSupplierOptions', () => {
  beforeEach(() => {
    vi.mocked(listSuppliers).mockReset()
  })

  it('keeps fetching until the last page', async () => {
    vi.mocked(listSuppliers)
      .mockResolvedValueOnce({
        content: [supplier('a'), supplier('b')],
        page: 0,
        size: 100,
        totalElements: 3,
        totalPages: 2,
      })
      .mockResolvedValueOnce({
        content: [supplier('c')],
        page: 1,
        size: 100,
        totalElements: 3,
        totalPages: 2,
      })

    const options = await orderApi.listSupplierOptions()

    expect(options.map((o) => o.id)).toEqual(['a', 'b', 'c'])
    expect(listSuppliers).toHaveBeenCalledTimes(2)
    expect(listSuppliers).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
    )
  })

  it('stops after one page when there is only one', async () => {
    vi.mocked(listSuppliers).mockResolvedValueOnce({
      content: [supplier('a')],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    })

    expect(await orderApi.listSupplierOptions()).toHaveLength(1)
    expect(listSuppliers).toHaveBeenCalledTimes(1)
  })
})

describe('orderApi against order-service', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  function respond(status: number, body: unknown) {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  }

  function lastCall(): { url: string; init: RequestInit } {
    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
    return { url, init }
  }

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('creates with POST /create and reads the id from the message', async () => {
    respond(200, { message: 'Request 12 created' })

    const created = await orderApi.createRequest({
      supplierId: 2,
      supplierName: 'The Deck',
      dropoffLocation: 'COM3',
      item: 'Tea',
      creditAmount: 3,
      deadline: '2099-01-01T00:00:00.000Z',
    })

    expect(created).toEqual({ id: 12, message: 'Request 12 created' })
    const { url, init } = lastCall()
    expect(url).toMatch(/\/create$/)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual(
      expect.objectContaining({ supplierId: 2, item: 'Tea', creditAmount: 3 }),
    )
  })

  it('posts each step with the request id in the body', async () => {
    for (const step of [
      'accept',
      'collect',
      'arrive',
      'drop',
      'cancel',
      'complete',
    ] as const) {
      respond(200, { message: `Request 5 ${step}ed` })
      await orderApi[step](5)
      const { url, init } = lastCall()
      expect(url).toMatch(new RegExp(`/${step}$`))
      expect(init.method).toBe('POST')
      expect(JSON.parse(init.body as string)).toEqual({ requestId: 5 })
    }
  })

  it('lists with the real paths and leaves out unset parameters', async () => {
    const page = {
      content: [],
      page: 0,
      size: 9,
      totalElements: 0,
      totalPages: 0,
    }
    respond(200, page)
    await orderApi.listOpenRequests({ page: 0, size: 9, sort: 'deadline' })
    expect(lastCall().url).toMatch(/\/requests\?page=0&size=9&sort=deadline$/)

    respond(200, page)
    await orderApi.listMyRequests({ role: 'courier', page: 1, size: 20 })
    expect(lastCall().url).toMatch(
      /\/requests\/mine\?role=courier&page=1&size=20$/,
    )

    respond(200, page)
    await orderApi.listMyRequests({
      role: 'requester',
      state: 'ARRIVED',
      page: 0,
      size: 10,
    })
    expect(lastCall().url).toContain('state=ARRIVED')
  })

  it("surfaces order-service's {message} error as the ApiError message", async () => {
    respond(409, { message: 'A requester cannot accept their own request' })

    const error = await orderApi.accept(1).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(
      'A requester cannot accept their own request',
    )
  })

  it('looks a username up once per user id', async () => {
    respond(200, { username: 'carol' })

    expect(await orderApi.getUsername(777)).toBe('carol')
    expect(await orderApi.getUsername(777)).toBe('carol')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(lastCall().url).toMatch(/\/users\/777$/)
  })
})
