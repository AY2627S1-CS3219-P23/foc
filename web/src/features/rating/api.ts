// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: rating-service REST calls through the shared apiFetch wrapper.
// Unlike order-service, rating-service answers real problem+json
// (ProblemDetailAdvice), so apiFetch's own error handling is enough.
// No mock variant: the real API was already merged (#252, #253) when
// this feature was built.
// Author review: [pending]
// 2026-10-30, Claude Code (Fable 5), issue #257: the public-profile
// calls — rating summary, received reviews (PagedResponse reused from
// the supplier domain; it matches rating-service's envelope exactly),
// and POST /reports.
// Author review: [pending]

import { apiFetch } from '@/lib/api/http'
import type { PagedResponse } from '@/features/supplier/types'
import type {
  MyRatingResponse,
  RatingResponse,
  RatingSummaryResponse,
  ReviewResponse,
  SubmitRatingInput,
  SubmitReportInput,
} from './types'

export const ratingApi = {
  // one submission per party per request; 409 when already rated
  submitRating(input: SubmitRatingInput): Promise<RatingResponse> {
    return apiFetch<RatingResponse>('rating', '/ratings', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },

  // the caller's own rating of one request; 403/404 when not a party or
  // the request is not completed (same eligibility gate as submitting)
  getMyRating(requestId: number): Promise<MyRatingResponse> {
    return apiFetch<MyRatingResponse>(
      'rating',
      `/ratings/mine?requestId=${requestId}`,
    )
  },

  // a user's received-rating aggregate; never 404s — an unknown user
  // answers {average: null, count: 0}
  getSummary(userId: string): Promise<RatingSummaryResponse> {
    return apiFetch<RatingSummaryResponse>(
      'rating',
      `/users/${encodeURIComponent(userId)}/ratings/summary`,
    )
  },

  // reviews received, newest first; rater names are not in the payload —
  // the browser hydrates them from raterId (D32)
  listReviews(
    userId: string,
    page: number,
    size: number,
  ): Promise<PagedResponse<ReviewResponse>> {
    return apiFetch<PagedResponse<ReviewResponse>>(
      'rating',
      `/users/${encodeURIComponent(userId)}/ratings?page=${page}&size=${size}`,
    )
  },

  // files a report; 400 on self-report or bad fields. The service keeps
  // every submission — no duplicate detection, so there is no 409 path.
  async submitReport(input: SubmitReportInput): Promise<void> {
    await apiFetch<unknown>('rating', '/reports', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },
}
