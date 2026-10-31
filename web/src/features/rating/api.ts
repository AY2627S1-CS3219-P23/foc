// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: rating-service REST calls through the shared apiFetch wrapper.
// Unlike order-service, rating-service answers real problem+json
// (ProblemDetailAdvice), so apiFetch's own error handling is enough.
// No mock variant: the real API was already merged (#252, #253) when
// this feature was built.
// Author review: [pending]

import { apiFetch } from '@/lib/api/http'
import type {
  MyRatingResponse,
  RatingResponse,
  SubmitRatingInput,
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
}
