// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: in-memory stand-in for order-service (and the credit balance), so
// the requester/courier pages can be used in the browser without the
// backend. Same signatures as the real orderApi. State lives in module
// memory: a page reload resets it. It starts with NO requests (only a
// supplier list and a balance), so create some through the UI.
// The mock's signed-in user is whoever GET /users/me says (the real
// user-service, or adminApiMock.ts under VITE_MOCK_ADMIN_API=true), read at
// the start of every call — so it always agrees with the pages' own idea of
// who is signed in, and fails loudly if /users/me does.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: rewritten to
// order-service's real contract (the author kept the mock rather than
// deleting it) and its rules, so the pages behave the same against either:
// a new request is PENDING and is reserved (CREATED) or REJECTED a moment
// later, standing in for credit-service; steps answer {"message"}; collect /
// arrive / drop are the assigned courier's and cancel / complete the
// requester's, anyone else gets a 404; a self-accept or an accept past the
// deadline is a 409; the open list is CREATED with the deadline ahead; and
// the expiry and 1-hour auto-complete timers run on every call. It is NOT a
// spec for the backend: order-service is.
// 2026-10-20: the state filter takes several states.
// Author review: [pending]
// 2026-10-29, Claude Code (Opus 5.5), issue #241: autoCompleteAt filled in
// as order-service works it out (the web app no longer knows the window;
// the mock plays order-service's default 1 h setting), and /dispute with
// its rules (category, note, the window, no window without an arrival
// time, a COLLECTED request's deadline).
// Author review: Ryan Ang, pending pull request review.

import { adminUserApi } from '@/features/user/adminApi'
import { ApiError } from '@/lib/api/http'
import type { CreditBalance } from '@/features/credit/types'
import type { PagedResponse } from '@/features/supplier/types'
import { MAX_DISPUTE_NOTE } from './status'
import type {
  CreateRequestInput,
  DisputeInput,
  ListMineParams,
  ListOpenParams,
  MessageResponse,
  RequestDetail,
  RequestStatus,
  RequestSummary,
  SupplierOption,
} from './types'

// the signed-in user, refreshed by enter() at the start of every call
let meId = 0
const names = new Map<number, string>()
const STARTING_CREDITS = 42
// order-service's ORDER_AUTO_COMPLETE_AFTER_MS default
const AUTO_COMPLETE_MS = 60 * 60 * 1000
const DISPUTE_CATEGORIES = ['NOT_DELIVERED', 'WRONG_ITEM', 'DAMAGED', 'OTHER']
// how long the stand-in credit-service takes to reserve a reward
const RESERVE_DELAY_MS = 1500

const suppliers: SupplierOption[] = [
  { id: '1', name: 'CoffeeBean@Com3', location: 'COM3 Level 1' },
  { id: '2', name: 'Printers@PCCommons', location: 'PC Commons, UTown' },
  { id: '3', name: 'Fine Foods UTown', location: 'Stephen Riady Centre' },
  { id: '4', name: 'Science Library Counter', location: 'Science Library' },
  { id: '5', name: 'Yusof Ishak House Coop', location: 'YIH Level 1' },
  { id: '6', name: 'UTown POPStation', location: 'UTown Plaza' },
  { id: '7', name: 'Mochtar Riady Building Cafe', location: 'BIZ 2, Level 4' },
  { id: '8', name: 'PGP Mailroom', location: 'Prince George’s Park' },
]

let nextId = 1
const requests: RequestDetail[] = []
// requests waiting for the stand-in credit-service, with when they arrived
const pendingSince = new Map<number, number>()
let myTotal = STARTING_CREDITS

// ---- rules ----

// Simulated latency, then the identity the call runs as.
async function enter() {
  await new Promise<void>((resolve) => setTimeout(resolve, 250))
  const user = await adminUserApi.getCurrentUser()
  meId = user.id
  names.set(user.id, user.username)
  sweep()
}

// order-service's error body is {"message"}; api.ts turns it into this
function fail(status: number, message: string): never {
  throw new ApiError(status, { status, detail: message })
}

function heldBy(userId: number) {
  return requests
    .filter(
      (r) =>
        r.requesterId === userId &&
        ['CREATED', 'ACCEPTED', 'COLLECTED', 'ARRIVED', 'DISPUTED'].includes(
          r.status,
        ),
    )
    .reduce((sum, r) => sum + r.creditAmount, 0)
}

// What would run in the background: credit-service answering a new
// request, the expiry sweep, and the 1-hour auto-complete.
function sweep() {
  const now = Date.now()
  for (const r of requests) {
    const since = pendingSince.get(r.id)
    if (
      r.status === 'PENDING' &&
      since !== undefined &&
      now - since >= RESERVE_DELAY_MS
    ) {
      pendingSince.delete(r.id)
      const available = myTotal - heldBy(r.requesterId)
      r.status =
        r.requesterId !== meId || r.creditAmount <= available
          ? 'CREATED'
          : 'REJECTED'
    }
    if (
      (r.status === 'CREATED' || r.status === 'ACCEPTED') &&
      new Date(r.deadline).getTime() <= now
    ) {
      r.status = 'EXPIRED'
    }
    const autoAt = autoCompleteAt(r)
    if (autoAt !== null && now >= new Date(autoAt).getTime()) {
      finish(r)
    }
  }
}

// as order-service's RequestsService.autoCompleteAt
function autoCompleteAt(r: RequestDetail): string | null {
  if (r.status !== 'ARRIVED' || !r.arrivedAt) return null
  return new Date(
    new Date(r.arrivedAt).getTime() + AUTO_COMPLETE_MS,
  ).toISOString()
}

function finish(r: RequestDetail) {
  r.status = 'COMPLETED'
  // the requester pays; the courier is paid
  if (r.requesterId === meId) myTotal -= r.creditAmount
  if (r.courierId === meId) myTotal += r.creditAmount
}

function find(id: number): RequestDetail {
  const r = requests.find((x) => x.id === id)
  if (!r) fail(404, 'Request not found')
  return r
}

// another party's step looks like an unknown request, as in order-service
function asRequester(id: number): RequestDetail {
  const r = find(id)
  if (r.requesterId !== meId) fail(404, 'Request not found')
  return r
}

function asCourier(id: number): RequestDetail {
  const r = find(id)
  if (r.courierId !== meId) fail(404, 'Request not found')
  return r
}

function move(
  r: RequestDetail,
  event: string,
  from: RequestStatus[],
  to: RequestStatus,
) {
  if (!from.includes(r.status))
    fail(409, `Cannot ${event} a request that is ${r.status}`)
  r.status = to
}

function summary(r: RequestDetail): RequestSummary {
  const {
    id,
    status,
    supplierName,
    dropoffLocation,
    item,
    creditAmount,
    deadline,
    createdAt,
    dispute,
  } = r
  return {
    id,
    status,
    supplierName,
    dropoffLocation,
    item,
    creditAmount,
    deadline,
    createdAt,
    autoCompleteAt: autoCompleteAt(r),
    dispute,
  }
}

function paged(
  all: RequestDetail[],
  params: ListOpenParams,
): PagedResponse<RequestSummary> {
  const sorted = [...all].sort((a, b) =>
    params.sort === 'deadline'
      ? a.deadline.localeCompare(b.deadline) || a.id - b.id
      : b.createdAt.localeCompare(a.createdAt) || b.id - a.id,
  )
  const start = params.page * params.size
  return {
    content: sorted.slice(start, start + params.size).map(summary),
    page: params.page,
    size: params.size,
    totalElements: sorted.length,
    totalPages: Math.max(1, Math.ceil(sorted.length / params.size)),
  }
}

const ok = (r: RequestDetail, outcome: string): MessageResponse => ({
  message: `Request ${r.id} ${outcome}`,
})

export const mockOrderApi = {
  async createRequest(
    input: CreateRequestInput,
  ): Promise<{ id: number; message: string }> {
    await enter()
    const missing = (
      ['supplierName', 'dropoffLocation', 'item'] as const
    ).filter((k) => !input[k]?.trim())
    const problems: string[] = []
    if (missing.length)
      problems.push(`Missing required fields: ${missing.join(', ')}`)
    if (input.creditAmount < 1) problems.push('creditAmount must be at least 1')
    if (new Date(input.deadline).getTime() <= Date.now())
      problems.push('deadline must be in the future')
    if (problems.length) fail(400, problems.join('; '))
    const now = new Date().toISOString()
    const r: RequestDetail = {
      id: nextId++,
      status: 'PENDING',
      requesterId: meId,
      courierId: null,
      supplierId: input.supplierId,
      supplierName: input.supplierName,
      dropoffLocation: input.dropoffLocation,
      item: input.item,
      creditAmount: input.creditAmount,
      deadline: input.deadline,
      createdAt: now,
      autoCompleteAt: null, // worked out on each read
      acceptedAt: null,
      collectedAt: null,
      arrivedAt: null,
    }
    requests.push(r)
    pendingSince.set(r.id, Date.now())
    return { id: r.id, message: `Request ${r.id} created` }
  },

  async listOpenRequests(params: ListOpenParams) {
    await enter()
    const now = Date.now()
    return paged(
      requests.filter(
        (r) => r.status === 'CREATED' && new Date(r.deadline).getTime() > now,
      ),
      params,
    )
  },

  async listMyRequests(params: ListMineParams) {
    await enter()
    return paged(
      requests.filter(
        (r) =>
          (params.role === 'requester' ? r.requesterId : r.courierId) ===
            meId &&
          (params.state === undefined || params.state.includes(r.status)),
      ),
      params,
    )
  },

  async getRequest(id: number): Promise<RequestDetail> {
    await enter()
    const r = find(id)
    if (r.requesterId !== meId && r.courierId !== meId)
      fail(404, 'Request not found')
    return { ...r, autoCompleteAt: autoCompleteAt(r) }
  },

  async accept(id: number) {
    await enter()
    const r = find(id)
    move(r, 'ACCEPT', ['CREATED'], 'ACCEPTED')
    if (r.requesterId === meId) {
      r.status = 'CREATED'
      fail(409, 'A requester cannot accept their own request')
    }
    if (new Date(r.deadline).getTime() <= Date.now()) {
      r.status = 'CREATED'
      fail(409, "The request's deadline has passed")
    }
    r.courierId = meId
    r.acceptedAt = new Date().toISOString()
    return ok(r, 'accepted')
  },

  async collect(id: number) {
    await enter()
    const r = asCourier(id)
    move(r, 'COLLECT', ['ACCEPTED'], 'COLLECTED')
    r.collectedAt = new Date().toISOString()
    return ok(r, 'collected')
  },

  async arrive(id: number) {
    await enter()
    const r = asCourier(id)
    move(r, 'ARRIVE', ['COLLECTED'], 'ARRIVED')
    r.arrivedAt = new Date().toISOString()
    return ok(r, 'arrived')
  },

  async drop(id: number) {
    await enter()
    const r = asCourier(id)
    move(r, 'DROP', ['ACCEPTED'], 'CREATED')
    r.courierId = null
    r.acceptedAt = null
    return ok(r, 'dropped')
  },

  async cancel(id: number) {
    await enter()
    const r = asRequester(id)
    move(r, 'CANCEL', ['CREATED'], 'CANCELLED')
    return ok(r, 'cancelled')
  },

  async complete(id: number) {
    await enter()
    const r = asRequester(id)
    if (r.status !== 'ARRIVED')
      fail(409, `Cannot COMPLETE a request that is ${r.status}`)
    finish(r)
    return ok(r, 'completed')
  },

  async dispute(id: number, input: DisputeInput) {
    await enter()
    if (!DISPUTE_CATEGORIES.includes(input.category))
      fail(400, 'Invalid value for category')
    const note = input.note?.trim() || null
    if (note && note.length > MAX_DISPUTE_NOTE)
      fail(400, `note must be at most ${MAX_DISPUTE_NOTE} characters`)
    if (input.category === 'OTHER' && !note)
      fail(400, 'note is required when the category is OTHER')
    const r = asRequester(id)
    const now = Date.now()
    if (r.status === 'ARRIVED' && !r.arrivedAt)
      fail(409, 'No arrival time is recorded for this request')
    const windowEnd = autoCompleteAt(r)
    if (windowEnd !== null && now >= new Date(windowEnd).getTime())
      fail(409, `The dispute window closed at ${windowEnd}`)
    if (r.status === 'COLLECTED' && new Date(r.deadline).getTime() > now)
      fail(409, "The request's deadline has not passed yet")
    move(r, 'DISPUTE', ['ARRIVED', 'COLLECTED'], 'DISPUTED')
    r.dispute = {
      category: input.category,
      disputeNote: note,
      raisedAt: new Date(now).toISOString(),
      autoEscalated: false,
      resolvedAt: null,
      resolvedBy: null,
      courierAmount: null,
      requesterAmount: null,
      resolutionNote: null,
    }
    return ok(r, 'disputed')
  },

  async getUsername(userId: number): Promise<string> {
    await enter()
    return names.get(userId) ?? `user${userId}`
  },

  async listSupplierOptions(): Promise<SupplierOption[]> {
    await enter()
    return suppliers
  },

  async getBalance(): Promise<CreditBalance> {
    await enter()
    const reserved = heldBy(meId)
    return { available: myTotal - reserved, reserved, total: myTotal }
  },
}
