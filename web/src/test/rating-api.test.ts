// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: ratingApi against a stubbed fetch — the submit body and path,
// /ratings/mine passing its wrapper through (null until rated), and
// rating-service's problem+json details surfacing as the ApiError
// message (house style: order-api.test.ts).
// Author review: [pending]
// 2026-10-30, Claude Code (Fable 5), issue #257: the public-profile
// calls — summary and review-page pass-through with exact URLs, the
// report POST body (blank details omitted by the modal, not here), and
// a 400's problem detail surfacing.
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

describe('ratingApi.getSummary', () => {
  it("asks for the user's summary and passes it through", async () => {
    respond(200, { average: 4.1666, count: 18 })
    await expect(ratingApi.getSummary('9')).resolves.toEqual({
      average: 4.1666,
      count: 18,
    })
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://gw/api/rating/users/9/ratings/summary',
    )
  })

  it('passes the unrated shape through (null average, not 0)', async () => {
    respond(200, { average: null, count: 0 })
    await expect(ratingApi.getSummary('9')).resolves.toEqual({
      average: null,
      count: 0,
    })
  })
})

describe('ratingApi.listReviews', () => {
  it('asks for one page of reviews and passes the envelope through', async () => {
    const page = {
      content: [
        {
          raterId: '3',
          stars: 5,
          text: 'Fast and friendly',
          createdAt: '2026-10-30T04:00:00Z',
        },
      ],
      page: 1,
      size: 5,
      totalElements: 6,
      totalPages: 2,
    }
    respond(200, page)

    await expect(ratingApi.listReviews('9', 1, 5)).resolves.toEqual(page)
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://gw/api/rating/users/9/ratings?page=1&size=5',
    )
  })
})

describe('ratingApi.submitReport', () => {
  it('POSTs /reports with the body as given', async () => {
    respond(201, {
      id: 'rpt-1',
      reportedUserId: '9',
      reporterId: '3',
      requestId: null,
      reason: 'NO_SHOW',
      details: 'Never arrived',
      createdAt: '2026-10-30T04:00:00Z',
    })

    await ratingApi.submitReport({
      reportedUserId: '9',
      reason: 'NO_SHOW',
      details: 'Never arrived',
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('http://gw/api/rating/reports')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      reportedUserId: '9',
      reason: 'NO_SHOW',
      details: 'Never arrived',
    })
  })

  it("surfaces a 400's problem+json detail (e.g. self-report) as the error message", async () => {
    respond(400, {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'You cannot report yourself',
    })

    const err: unknown = await ratingApi
      .submitReport({ reportedUserId: '3', reason: 'OTHER' })
      .catch((e: unknown) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(400)
    expect((err as ApiError).message).toBe('You cannot report yourself')
  })
})
