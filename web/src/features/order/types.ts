// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: Order domain types for the requester/courier pages, drawn from
// the wireframes in web/docs/wireframes/ (create-request, my-requests,
// open-requests, active-delivery, order-history, dispute-flow). The
// order-service API is not built yet, so the field names and the status
// set are the frontend's working assumption — align them with the real
// contract once the order-service owner publishes it.
// Author review: [pending]

export type OrderStatus =
  | 'OPEN'
  | 'ACCEPTED'
  | 'PICKED_UP'
  | 'DELIVERED' // courier marked arrival; waiting on the requester
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'DISPUTED'

export interface Order {
  id: number
  status: OrderStatus
  description: string
  // credits reserved from the requester, paid to the courier on completion
  reward: number
  // supplier details are snapshotted onto the order, so a later supplier
  // edit does not rewrite history
  supplierId: string
  supplierName: string
  pickupLocation: string
  dropoffLocation: string
  deadline: string // ISO-8601 instant; an unaccepted order expires here
  createdAt: string
  updatedAt: string
  deliveredAt: string | null // starts the 1-hour auto-complete window
  requesterId: number
  requesterUsername: string
  courierId: number | null
  courierUsername: string | null
}

export interface CreateOrderInput {
  supplierId: string
  dropoffLocation: string
  description: string
  reward: number
  deadline: string // ISO-8601 instant
}

export type OrderRole = 'requester' | 'courier'

// active = not yet finished; history = completed, cancelled or expired
// current = everything except COMPLETED (open, in progress, disputed,
// cancelled, expired): what My Requests needs, so finished orders never
// crowd out one the requester can still act on
export type OrderScope = 'active' | 'history' | 'current' | 'all'

export interface ListOrdersParams {
  as: OrderRole
  scope: OrderScope
  page: number // 0-based
  size: number
}

export interface DisputeInput {
  reason: string
  description: string
}

export interface SupplierOption {
  id: string
  name: string
  location: string
}
