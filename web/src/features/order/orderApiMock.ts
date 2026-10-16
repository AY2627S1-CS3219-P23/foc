// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: in-memory stand-in for order-service (and the credit balance), so
// the requester/courier pages can be used in the browser before the
// backend exists. Same signatures as the real orderApi. It enforces the
// rules the real service must (nobody accepts their own request; only one
// courier wins an accept; transitions only from valid states; deadlines
// expire open orders; a delivered order auto-completes after an hour) so
// the pages' error handling can be exercised, but it is NOT a spec for the
// backend. It starts with NO orders (only a supplier list and a balance),
// so create some through the UI. State lives in module memory: a page
// reload resets it.
// The mock's signed-in user is user 3, nus_courier_99, to match
// adminApiMock.ts (VITE_MOCK_ADMIN_API=true supplies GET /users/me).
//
// TEMPORARY: delete with the switch in api.ts once order-service is live.
// Author review: [pending]

import { ApiError } from '@/lib/api/http'
import type { PagedResponse } from '@/features/supplier/types'
import { AUTO_COMPLETE_MS, isTerminal } from './status'
import type {
  CreateOrderInput,
  CreditBalance,
  DisputeInput,
  ListOrdersParams,
  Order,
  OrderStatus,
  SupplierOption,
} from './types'

const ME_ID = 3
const ME_NAME = 'nus_courier_99'
const STARTING_CREDITS = 42

const MIN = 60_000
const HOUR = 60 * MIN

const suppliers: SupplierOption[] = [
  { id: 's1', name: 'CoffeeBean@Com3', location: 'COM3 Level 1' },
  { id: 's2', name: 'Printers@PCCommons', location: 'PC Commons, UTown' },
  { id: 's3', name: 'Fine Foods UTown', location: 'Stephen Riady Centre' },
  { id: 's4', name: 'Science Library Counter', location: 'Science Library' },
  { id: 's5', name: 'Yusof Ishak House Coop', location: 'YIH Level 1' },
  { id: 's6', name: 'UTown POPStation', location: 'UTown Plaza' },
  { id: 's7', name: 'Mochtar Riady Building Cafe', location: 'BIZ 2, Level 4' },
  { id: 's8', name: 'PGP Mailroom', location: 'Prince George’s Park' },
]

const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()

let nextId = 1
const orders: Order[] = []

// ---- rules ----

function delay() {
  return new Promise<void>((resolve) => setTimeout(resolve, 250))
}

function fail(status: number, title: string, detail: string): never {
  throw new ApiError(status, { status, title, detail })
}

// Time-driven transitions (the real service would do these in the
// background): unaccepted orders past their deadline expire, and a
// delivered order the requester never answered completes itself.
function sweep() {
  const now = Date.now()
  for (const o of orders) {
    if (o.status === 'OPEN' && new Date(o.deadline).getTime() <= now) {
      o.status = 'EXPIRED'
      o.updatedAt = new Date(now).toISOString()
    } else if (
      o.status === 'DELIVERED' &&
      o.deliveredAt &&
      new Date(o.deliveredAt).getTime() + AUTO_COMPLETE_MS <= now
    ) {
      complete(o)
    }
  }
}

let myTotal = STARTING_CREDITS

function complete(o: Order) {
  o.status = 'COMPLETED'
  o.updatedAt = new Date().toISOString()
  if (o.requesterId === ME_ID) myTotal -= o.reward
  if (o.courierId === ME_ID) myTotal += o.reward
}

// Credits stay reserved while an order of mine is still unsettled.
function myReserved() {
  return orders
    .filter((o) => o.requesterId === ME_ID && !isTerminal(o.status))
    .reduce((sum, o) => sum + o.reward, 0)
}

function find(id: number): Order {
  sweep()
  const order = orders.find((o) => o.id === id)
  if (!order) fail(404, 'Not found', `Request #${id} does not exist.`)
  if (
    order.requesterId !== ME_ID &&
    order.courierId !== ME_ID &&
    order.status !== 'OPEN'
  ) {
    fail(403, 'Forbidden', 'You are not part of this request.')
  }
  return order
}

function touch(o: Order, status: OrderStatus) {
  o.status = status
  o.updatedAt = new Date().toISOString()
  return { ...o }
}

function requireRequester(o: Order) {
  if (o.requesterId !== ME_ID) {
    fail(403, 'Forbidden', 'Only the requester can do this.')
  }
}

function requireCourier(o: Order) {
  if (o.courierId !== ME_ID) {
    fail(403, 'Forbidden', 'Only the assigned courier can do this.')
  }
}

function requireStatus(o: Order, ...allowed: OrderStatus[]) {
  if (!allowed.includes(o.status)) {
    fail(
      409,
      'Invalid state',
      `This request is ${o.status.toLowerCase().replace('_', ' ')} — that action is no longer possible.`,
    )
  }
}

function paged(all: Order[], page: number, size: number): PagedResponse<Order> {
  return {
    content: all.slice(page * size, page * size + size).map((o) => ({ ...o })),
    page,
    size,
    totalElements: all.length,
    totalPages: Math.max(1, Math.ceil(all.length / size)),
  }
}

export const mockOrderApi = {
  async createOrder(input: CreateOrderInput): Promise<Order> {
    await delay()
    sweep()
    const supplier = suppliers.find((s) => s.id === input.supplierId)
    if (!supplier) fail(400, 'Bad request', 'Pick a supplier.')
    if (input.reward < 1)
      fail(400, 'Bad request', 'Reward must be at least 1 credit.')
    if (new Date(input.deadline).getTime() <= Date.now()) {
      fail(400, 'Bad request', 'Deadline must be in the future.')
    }
    if (input.reward > myTotal - myReserved()) {
      fail(
        409,
        'Insufficient credits',
        `Insufficient credits. Need ${input.reward}, you have ${myTotal - myReserved()}.`,
      )
    }
    const now = new Date().toISOString()
    const order: Order = {
      id: nextId++,
      status: 'OPEN',
      description: input.description,
      reward: input.reward,
      supplierId: supplier.id,
      supplierName: supplier.name,
      pickupLocation: supplier.location,
      dropoffLocation: input.dropoffLocation,
      deadline: input.deadline,
      createdAt: now,
      updatedAt: now,
      deliveredAt: null,
      requesterId: ME_ID,
      requesterUsername: ME_NAME,
      courierId: null,
      courierUsername: null,
    }
    orders.unshift(order)
    return { ...order }
  },

  async listOpenOrders(page: number, size: number) {
    await delay()
    sweep()
    const open = orders
      .filter((o) => o.status === 'OPEN')
      .sort((a, b) => a.deadline.localeCompare(b.deadline))
    return paged(open, page, size)
  },

  async listMyOrders(params: ListOrdersParams) {
    await delay()
    sweep()
    const mine = orders
      .filter((o) =>
        params.as === 'requester'
          ? o.requesterId === ME_ID
          : o.courierId === ME_ID,
      )
      .filter((o) =>
        params.scope === 'all'
          ? true
          : params.scope === 'active'
            ? !isTerminal(o.status)
            : isTerminal(o.status),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return paged(mine, params.page, params.size)
  },

  async getOrder(id: number): Promise<Order> {
    await delay()
    return { ...find(id) }
  },

  async acceptOrder(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    if (o.requesterId === ME_ID) {
      fail(403, 'Forbidden', 'You cannot accept your own request.')
    }
    requireStatus(o, 'OPEN')
    o.courierId = ME_ID
    o.courierUsername = ME_NAME
    return touch(o, 'ACCEPTED')
  },

  async markPickedUp(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireCourier(o)
    requireStatus(o, 'ACCEPTED')
    return touch(o, 'PICKED_UP')
  },

  async markArrived(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireCourier(o)
    requireStatus(o, 'PICKED_UP')
    o.deliveredAt = new Date().toISOString()
    return touch(o, 'DELIVERED')
  },

  // before pickup only: the request goes back to the open feed
  async courierCancel(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireCourier(o)
    requireStatus(o, 'ACCEPTED')
    o.courierId = null
    o.courierUsername = null
    return touch(o, 'OPEN')
  },

  async cancelOrder(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireRequester(o)
    requireStatus(o, 'OPEN')
    return touch(o, 'CANCELLED')
  },

  async confirmDelivery(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireRequester(o)
    requireStatus(o, 'DELIVERED')
    complete(o)
    return { ...o }
  },

  async disputeOrder(id: number, input: DisputeInput): Promise<Order> {
    await delay()
    const o = find(id)
    requireRequester(o)
    requireStatus(o, 'DELIVERED')
    if (!input.description.trim()) {
      fail(400, 'Bad request', 'Describe what went wrong.')
    }
    return touch(o, 'DISPUTED')
  },

  // a copy of an expired/cancelled order, open again with a fresh deadline
  async reReleaseOrder(id: number): Promise<Order> {
    await delay()
    const o = find(id)
    requireRequester(o)
    requireStatus(o, 'EXPIRED', 'CANCELLED')
    return this.createOrder({
      supplierId: o.supplierId,
      dropoffLocation: o.dropoffLocation,
      description: o.description,
      reward: o.reward,
      deadline: iso(4 * HOUR),
    })
  },

  async listSupplierOptions(): Promise<SupplierOption[]> {
    await delay()
    return suppliers.map((s) => ({ ...s }))
  },

  async getBalance(): Promise<CreditBalance> {
    await delay()
    sweep()
    const reserved = myReserved()
    return { available: myTotal - reserved, reserved, total: myTotal }
  },
}
