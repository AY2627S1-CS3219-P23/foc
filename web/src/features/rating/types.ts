// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: rating-service response/request shapes, mirroring its DTOs
// (RatingResponse, MyRatingResponse, SubmitRatingRequest). Ids are strings
// on the wire — rating-service keys everything by opaque string ids even
// though order-service request ids are numbers on the web side.
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
