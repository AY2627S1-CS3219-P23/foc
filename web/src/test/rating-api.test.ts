// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: ratingApi against a stubbed fetch — the submit body and path,
// /ratings/mine passing its wrapper through (null until rated), and
// rating-service's problem+json details surfacing as the ApiError
// message (house style: order-api.test.ts).
// Author review: [pending]

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ratingApi } from '@/features/rating/api'
import type { RatingResponse } from '@/features/rating/types'
import { ApiError } from '@/lib/api/http'

// the service base URLs are read once at import, as http.test.ts mocks them
vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://gw/api/user',
    supplier: 'http://gw/api/supplier',
    order: 'http://gw/api/order',
    credit: 'http://gw/api/credit',
    notification: 'http://gw/api/notification',
    rating: 'http://gw/api/rating',
  },
}))

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function respond(status: number, body: unknown) {
  fetchMock.mockResolvedValueOnce(
    new Response(JSON.stringify(body), {
      status,
      headers: {
        'Content-Type':
          status < 400 ? 'application/json' : 'application/problem+json',
      },
    }),
  )
}

const rated: RatingResponse = {
  id: 'rtg-1',
  requestId: '7',
  raterId: '3',
  rateeId: '9',
  stars: 4,
  text: 'Fast and friendly',
  createdAt: '2026-10-30T04:00:00Z',
}

describe('ratingApi.submitRating', () => {
  it('POSTs /ratings with the body as given and returns the rating', async () => {
    respond(201, rated)

    const result = await ratingApi.submitRating({
      requestId: '7',
      stars: 4,
      text: 'Fast and friendly',
    })

    expect(result).toEqual(rated)
    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('http://gw/api/rating/ratings')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      requestId: '7',
      stars: 4,
      text: 'Fast and friendly',
    })
  })

  it("surfaces a 409's problem+json detail as the error message", async () => {
    respond(409, {
      type: 'about:blank',
      title: 'Conflict',
      status: 409,
      detail: 'You have already rated this request',
    })

    const err: unknown = await ratingApi
      .submitRating({ requestId: '7', stars: 4 })
      .catch((e: unknown) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(409)
    expect((err as ApiError).message).toBe(
      'You have already rated this request',
    )
  })
})

describe('ratingApi.getMyRating', () => {
  it('asks /ratings/mine for one request and passes the wrapper through', async () => {
    respond(200, { rating: null })
    await expect(ratingApi.getMyRating(7)).resolves.toEqual({ rating: null })
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://gw/api/rating/ratings/mine?requestId=7',
    )

    respond(200, { rating: rated })
    await expect(ratingApi.getMyRating(7)).resolves.toEqual({ rating: rated })
  })
})
