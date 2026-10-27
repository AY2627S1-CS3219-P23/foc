// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-27, issue #242.
// Scope: order-service's admin calls (team decisions DSP-5 and DSP-10,
// docs/disputes.md): GET /requests/admin, POST /requests/admin/statuses
// and POST /retry-settlement, for the admin dashboard's failed and stuck
// orders. ADMIN / OWNER only; anyone else gets a 403. Kept apart from
// orderApi (as adminUserApi is from the profile calls), so the
// requester/courier dev mock does not have to stand in for them.
// Author review: Ryan Ang, pending pull request review.

import type { PagedResponse } from '@/features/supplier/types'
import { orderFetch, query } from './api'
import type {
  ListAdminParams,
  MessageResponse,
  RequestStatus,
  RequestSummary,
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

  // the current state of up to 100 requests; an unknown id answers null
  getStatuses(ids: string[]): Promise<Record<string, RequestStatus | null>> {
    return orderFetch('/requests/admin/statuses', {
      method: 'POST',
      body: JSON.stringify({ ids }),
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
