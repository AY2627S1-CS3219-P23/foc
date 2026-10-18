// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-18.
// Scope: orderApi's supplier picker loads every page of suppliers, not
// just the first (PR review, LeongWZ).
// Author review: [pending]

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listSuppliers } from '@/features/supplier/api'
import { orderApi } from '@/features/order/api'
import type { Supplier } from '@/features/supplier/types'

vi.mock('@/features/supplier/api', () => ({ listSuppliers: vi.fn() }))

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
