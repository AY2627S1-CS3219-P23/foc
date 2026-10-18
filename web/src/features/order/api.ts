// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Order Service REST calls through the shared apiFetch wrapper.
// VITE_MOCK_ORDER_API=true (in web/.env.local) swaps in the in-memory mock
// (orderApiMock.ts) on the dev server only; production builds always use
// the real calls, and the mock is imported lazily so it is left out of the
// bundle.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: the real order-service
// contract replaces the placeholder paths — POST /create and one POST per
// step with {"requestId"}, GET /requests, /requests/mine, /requests/{id}.
// Steps answer {"message"}, so the pages reload instead of patching in a
// returned order. order-service's error bodies are {"message"} rather than
// problem+json, so they are re-thrown as ApiErrors carrying that message.
// Usernames come from user-service's GET /users/{id}, cached (the author's
// decision; order-service returns ids only). Dispute and re-release are
// gone: the backend has neither.
// Author review: [pending]

import { creditApi } from '@/features/credit/api'
import { listSuppliers } from '@/features/supplier/api'
import type { PagedResponse } from '@/features/supplier/types'
import { ApiError, apiFetch, type ApiFetchInit } from '@/lib/api/http'
import type {
  CreateRequestInput,
  ListMineParams,
  ListOpenParams,
  MessageResponse,
  RequestDetail,
  RequestSummary,
  SupplierOption,
} from './types'

// apiFetch for order-service: its 400/404/409 bodies are {"message"}, which
// apiFetch (expecting problem+json) would turn into "Request failed (409)".
async function orderFetch<T>(path: string, init?: ApiFetchInit): Promise<T> {
  try {
    return await apiFetch<T>('order', path, init)
  } catch (err) {
    const message = (err instanceof ApiError &&
      (err.problem as { message?: unknown } | null)?.message) as unknown
    if (err instanceof ApiError && typeof message === 'string' && message) {
      throw new ApiError(
        err.status,
        { status: err.status, detail: message },
        err.retryAfter,
      )
    }
    throw err
  }
}

// Optional parameters are left out when undefined: URLSearchParams would
// send them as the literal string "undefined".
function query(params: Record<string, string | number | undefined>) {
  const q = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) q.set(key, String(value))
  }
  return `?${q}`
}

function step(path: string, requestId: number): Promise<MessageResponse> {
  return orderFetch<MessageResponse>(path, {
    method: 'POST',
    body: JSON.stringify({ requestId }),
  })
}

// "Request 12 created" -> 12
function createdId(message: string): number {
  const match = /Request (\d+)/.exec(message)
  return match ? Number(match[1]) : NaN
}

const usernames = new Map<number, Promise<string>>()

const httpOrderApi = {
  // the signed-in user becomes the requester; the request starts PENDING
  async createRequest(
    input: CreateRequestInput,
  ): Promise<{ id: number; message: string }> {
    const { message } = await orderFetch<MessageResponse>('/create', {
      method: 'POST',
      body: JSON.stringify(input),
    })
    return { id: createdId(message), message }
  },

  // open errands for couriers: CREATED, deadline still ahead
  listOpenRequests(
    params: ListOpenParams,
  ): Promise<PagedResponse<RequestSummary>> {
    return orderFetch(`/requests${query({ ...params })}`)
  },

  // the signed-in user's requests on one side
  listMyRequests(
    params: ListMineParams,
  ): Promise<PagedResponse<RequestSummary>> {
    return orderFetch(`/requests/mine${query({ ...params })}`)
  },

  getRequest(id: number): Promise<RequestDetail> {
    return orderFetch<RequestDetail>(`/requests/${id}`)
  },

  // courier steps
  accept: (id: number) => step('/accept', id),
  collect: (id: number) => step('/collect', id),
  arrive: (id: number) => step('/arrive', id),
  drop: (id: number) => step('/drop', id),

  // requester steps
  cancel: (id: number) => step('/cancel', id),
  complete: (id: number) => step('/complete', id),

  // the create form's affordability check; credit-service's own endpoint
  getBalance: () => creditApi.getBalance(),

  // user-service's public profile; one lookup per user id per page load
  getUsername(userId: number): Promise<string> {
    let name = usernames.get(userId)
    if (!name) {
      name = apiFetch<{ username: string }>('user', `/users/${userId}`).then(
        (profile) => profile.username,
      )
      // a failed lookup is not cached, so the next render can retry it
      name.catch(() => usernames.delete(userId))
      usernames.set(userId, name)
    }
    return name
  },

  // the pickers on the create form
  // Every supplier, not just the first page: a supplier past the page
  // size would otherwise be silently impossible to request from.
  async listSupplierOptions(): Promise<SupplierOption[]> {
    const options: SupplierOption[] = []
    for (let page = 0; ; page++) {
      const result = await listSuppliers({ page, size: 100, sort: 'name,asc' })
      for (const s of result.content) {
        options.push({ id: s.id, name: s.name, location: s.location })
      }
      if (page + 1 >= result.totalPages) return options
    }
  },
}

function loadMock() {
  return import('./orderApiMock').then((m) => m.mockOrderApi)
}

const devMockOrderApi: typeof httpOrderApi = {
  createRequest: async (input) => (await loadMock()).createRequest(input),
  listOpenRequests: async (params) =>
    (await loadMock()).listOpenRequests(params),
  listMyRequests: async (params) => (await loadMock()).listMyRequests(params),
  getRequest: async (id) => (await loadMock()).getRequest(id),
  accept: async (id) => (await loadMock()).accept(id),
  collect: async (id) => (await loadMock()).collect(id),
  arrive: async (id) => (await loadMock()).arrive(id),
  drop: async (id) => (await loadMock()).drop(id),
  cancel: async (id) => (await loadMock()).cancel(id),
  complete: async (id) => (await loadMock()).complete(id),
  getBalance: async () => (await loadMock()).getBalance(),
  getUsername: async (id) => (await loadMock()).getUsername(id),
  listSupplierOptions: async () => (await loadMock()).listSupplierOptions(),
}

export const orderApi: typeof httpOrderApi =
  import.meta.env.DEV && import.meta.env.VITE_MOCK_ORDER_API === 'true'
    ? devMockOrderApi
    : httpOrderApi
