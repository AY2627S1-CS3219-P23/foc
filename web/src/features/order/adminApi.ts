// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-27, issue #242.
// Scope: order-service's admin calls (team decisions DSP-5 and DSP-10,
// docs/disputes.md): GET /requests/admin, POST /requests/admin/statuses
// and POST /retry-settlement, for the admin dashboard's failed and stuck
// orders. ADMIN / OWNER only; anyone else gets a 403. Kept apart from
// orderApi (as adminUserApi is from the profile calls), so the
// requester/courier dev mock does not have to stand in for them.
// Author review: Ryan Ang, pending pull request review.
// 2026-10-29, Claude Code (Opus 5.5), issue #241: the dispute queue
// (GET /requests/admin?state=DISPUTED, DSP-3) and POST /resolve (DSP-5,
// DSP-8).
// Author review: Ryan Ang, pending pull request review.

import type { PagedResponse } from '@/features/supplier/types'
import { orderFetch, query } from './api'
import type {
  ListAdminParams,
  MessageResponse,
  RequestStatus,
  RequestSummary,
  ResolveInput,
} from './types'

export const orderAdminApi = {
  // every user's requests; the lists are sent comma-separated
  listRequests(
    params: ListAdminParams,
  ): Promise<PagedResponse<RequestSummary>> {
    return orderFetch(
      `/requests/admin${query({
        ...params,
        state: params.state?.join(','),
        closeReason: params.closeReason?.join(','),
      })}`,
    )
  },

  // the dispute queue: every DISPUTED request, with its dispute; its
  // totalElements is the dashboard's count badge
  listDisputes(
    page: number,
    size: number,
  ): Promise<PagedResponse<RequestSummary>> {
    return orderFetch(
      `/requests/admin${query({ state: 'DISPUTED', page, size })}`,
    )
  },

  // the current state of up to 100 requests; an unknown id answers null
  getStatuses(ids: string[]): Promise<Record<string, RequestStatus | null>> {
    return orderFetch('/requests/admin/statuses', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    })
  },

  // divides a DISPUTED request's held reward; 409 if the admin is a party
  // to it or it is no longer DISPUTED
  resolve(requestId: number, input: ResolveInput): Promise<MessageResponse> {
    return orderFetch('/resolve', {
      method: 'POST',
      body: JSON.stringify({ requestId, ...input }),
    })
  },

  // sends a finished request's settlement event again; 409 if the request
  // is still active or has no such event
  retrySettlement(requestId: number): Promise<MessageResponse> {
    return orderFetch('/retry-settlement', {
      method: 'POST',
      body: JSON.stringify({ requestId }),
    })
  },
}
