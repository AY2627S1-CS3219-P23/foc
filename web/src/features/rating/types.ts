// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: rating-service response/request shapes, mirroring its DTOs
// (RatingResponse, MyRatingResponse, SubmitRatingRequest). Ids are strings
// on the wire — rating-service keys everything by opaque string ids even
// though order-service request ids are numbers on the web side.
// Author review: [pending]
// 2026-10-30, Claude Code (Fable 5), issue #257: the public-profile
// shapes — rating summary, received reviews, and user reports
// (RatingSummaryResponse, ReviewResponse, SubmitReportRequest DTOs).
// Author review: [pending]

export interface RatingResponse {
  id: string
  requestId: string
  raterId: string
  rateeId: string
  stars: number
  text: string | null
  createdAt: string
}

// GET /ratings/mine: rating is null until the caller rates the request.
export interface MyRatingResponse {
  rating: RatingResponse | null
}

export interface SubmitRatingInput {
  requestId: string
  stars: number
  text?: string
}

// Mirrors SubmitRatingRequest.TEXT_MAX; the service rejects longer text.
export const MAX_RATING_TEXT = 1000

// GET /users/{id}/ratings/summary: average is the raw mean (the UI
// rounds) and is null — not 0 — until the user has a rating.
export interface RatingSummaryResponse {
  average: number | null
  count: number
}

// One review in GET /users/{id}/ratings. Deliberately narrower than
// RatingResponse (no rating/request ids on the wire); the rater's
// username hydrates at read time from raterId (D32).
export interface ReviewResponse {
  raterId: string
  stars: number
  text: string | null
  createdAt: string
}

// Mirrors rating-service's ReportReason enum, British spelling included.
export type ReportReason =
  'NO_SHOW' | 'ABUSIVE_BEHAVIOUR' | 'UNSAFE_HANDLING' | 'OTHER'

export const reportReasonLabels: Record<ReportReason, string> = {
  NO_SHOW: 'No-show',
  ABUSIVE_BEHAVIOUR: 'Abusive behaviour',
  UNSAFE_HANDLING: 'Unsafe handling',
  OTHER: 'Other',
}

export interface SubmitReportInput {
  reportedUserId: string
  reason: ReportReason
  details?: string
  requestId?: string
}

// Mirrors SubmitReportRequest.DETAILS_MAX.
export const REPORT_DETAILS_MAX = 1000
