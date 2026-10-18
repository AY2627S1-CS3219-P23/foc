// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Order Service REST calls through the shared apiFetch wrapper.
// The order-service endpoints do not exist yet, so the paths below are the
// frontend's PLACEHOLDER contract (verbs-as-sub-resources for the state
// transitions, which the backend enforces) — change them here, in one
// place, when the real API lands.
//
// TEMPORARY: VITE_MOCK_ORDER_API=true (in web/.env.local) swaps in the
// in-memory mock (orderApiMock.ts) so the pages can be used before the
// backend exists. Dev server only: production builds always use the real
// calls, and the mock is imported lazily so it is left out of the bundle.
// Author review: [pending]

import { creditApi } from '@/features/credit/api'
import { apiFetch } from '@/lib/api/http'
import { listSuppliers } from '@/features/supplier/api'
import type { PagedResponse } from '@/features/supplier/types'
import type {
  CreateOrderInput,
  DisputeInput,
  ListOrdersParams,
  Order,
  SupplierOption,
} from './types'

// Optional filters are left out when undefined: URLSearchParams would send
// them as the literal string "undefined".
function pageQuery(
  page: number,
  size: number,
  extra: Record<string, string | undefined> = {},
) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(extra)) {
    if (value !== undefined) query.set(key, value)
  }
  query.set('page', String(page))
  query.set('size', String(size))
  return `?${query}`
}

const httpOrderApi = {
  createOrder(input: CreateOrderInput): Promise<Order> {
    return apiFetch<Order>('order', '/orders', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },

  // open orders from every requester, oldest deadline first
  listOpenOrders(page: number, size: number): Promise<PagedResponse<Order>> {
    return apiFetch('order', `/orders/open${pageQuery(page, size)}`)
  },

  listMyOrders(params: ListOrdersParams): Promise<PagedResponse<Order>> {
    return apiFetch(
      'order',
      `/orders/me${pageQuery(params.page, params.size, {
        as: params.as,
        scope: params.scope,
      })}`,
    )
  },

  getOrder(id: number): Promise<Order> {
    return apiFetch<Order>('order', `/orders/${id}`)
  },

  // the create form's affordability check; credit-service's own endpoint
  getBalance: () => creditApi.getBalance(),

  // courier
  acceptOrder: (id: number) => transition(id, 'accept'),
  markPickedUp: (id: number) => transition(id, 'pickup'),
  markArrived: (id: number) => transition(id, 'arrive'),
  courierCancel: (id: number) => transition(id, 'courier-cancel'),

  // requester
  cancelOrder: (id: number) => transition(id, 'cancel'),
  confirmDelivery: (id: number) => transition(id, 'confirm'),
  reReleaseOrder: (id: number) => transition(id, 're-release'),
  disputeOrder(id: number, input: DisputeInput): Promise<Order> {
    return transition(id, 'dispute', input)
  },

  // the pickers on the create form
  async listSupplierOptions(): Promise<SupplierOption[]> {
    const page = await listSuppliers({ page: 0, size: 100, sort: 'name,asc' })
    return page.content.map((s) => ({
      id: s.id,
      name: s.name,
      location: s.location,
    }))
  },
}

function transition(id: number, action: string, body?: unknown) {
  return apiFetch<Order>('order', `/orders/${id}/${action}`, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

function loadMock() {
  return import('./orderApiMock').then((m) => m.mockOrderApi)
}

const devMockOrderApi: typeof httpOrderApi = {
  createOrder: async (input) => (await loadMock()).createOrder(input),
  listOpenOrders: async (page, size) =>
    (await loadMock()).listOpenOrders(page, size),
  listMyOrders: async (params) => (await loadMock()).listMyOrders(params),
  getOrder: async (id) => (await loadMock()).getOrder(id),
  acceptOrder: async (id) => (await loadMock()).acceptOrder(id),
  markPickedUp: async (id) => (await loadMock()).markPickedUp(id),
  markArrived: async (id) => (await loadMock()).markArrived(id),
  courierCancel: async (id) => (await loadMock()).courierCancel(id),
  cancelOrder: async (id) => (await loadMock()).cancelOrder(id),
  confirmDelivery: async (id) => (await loadMock()).confirmDelivery(id),
  reReleaseOrder: async (id) => (await loadMock()).reReleaseOrder(id),
  disputeOrder: async (id, input) => (await loadMock()).disputeOrder(id, input),
  listSupplierOptions: async () => (await loadMock()).listSupplierOptions(),
  getBalance: async () => (await loadMock()).getBalance(),
}

export const orderApi: typeof httpOrderApi =
  import.meta.env.DEV && import.meta.env.VITE_MOCK_ORDER_API === 'true'
    ? devMockOrderApi
    : httpOrderApi
