// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Order domain types for the requester/courier pages, drawn from
// the wireframes in web/docs/wireframes/ (create-request, my-requests,
// open-requests, active-delivery, order-history, dispute-flow).
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: replaced the
// placeholder contract with order-service's real one (its README,
// "Endpoints" and "Reads"): the request states, RequestSummary (list
// items) and RequestDetail (GET /requests/{id}), the /create body, and the
// list parameters. Where the two differed the frontend was adapted to the
// backend (author decision); dispute and re-release are gone, since the
// backend has neither.
// 2026-10-20: ListMineParams.state is a list (order-service takes several
// states, comma-separated).
// 2026-10-26, Claude Code (Sonnet 5.5), issue #227 (N8.4): SupplierOption
// carries the caller's `bookmarked` flag.
// Author review: [pending]

// order-service's OrderStates, by name
export type RequestStatus =
  | 'PENDING' // waiting for credit-service to reserve the reward
  | 'CREATED' // reward reserved: open for couriers
  | 'ACCEPTED'
  | 'COLLECTED' // courier has the item
  | 'ARRIVED' // courier at the drop-off; waiting on the requester
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REJECTED' // the reward could not be reserved

// One request in a list (GET /requests, /requests/mine)
export interface RequestSummary {
  id: number
  status: RequestStatus
  supplierName: string
  dropoffLocation: string
  item: string
  creditAmount: number
  deadline: string // ISO-8601 instant
  createdAt: string
}

// One request in full (GET /requests/{id}): its requester, its courier, or
// an admin may read it
export interface RequestDetail extends RequestSummary {
  requesterId: number
  courierId: number | null
  supplierId: number
  acceptedAt: string | null
  collectedAt: string | null
  arrivedAt: string | null // starts the 1-hour auto-complete window
}

// POST /create; the requester is the signed-in user
export interface CreateRequestInput {
  supplierId: number
  supplierName: string
  dropoffLocation: string
  item: string
  creditAmount: number
  deadline: string // ISO-8601 instant
}

// What every POST step answers, e.g. "Request 5 accepted"
export interface MessageResponse {
  message: string
}

export type RequestRole = 'requester' | 'courier'

// createdAt: newest first (the default); deadline: nearest first
export type RequestSort = 'createdAt' | 'deadline'

export interface ListOpenParams {
  page: number // 0-based
  size: number
  sort?: RequestSort
}

export interface ListMineParams extends ListOpenParams {
  role: RequestRole
  // any of these states, sent comma-separated; omitted: every state
  state?: RequestStatus[]
}

export interface SupplierOption {
  id: string // supplier-service ids are strings here; sent as a number
  name: string
  location: string
  bookmarked?: boolean // the caller's bookmark; bookmarked options list first
}
