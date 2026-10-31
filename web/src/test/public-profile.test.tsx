// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the public profile page — summary and reviews rendering with
// rater names hydrating through the real usernames cache (stubbed
// fetch serves user-service; house style: usernames.test.ts), the
// no-reviews empty state, the user-not-found gate, the Report User
// dialog's validation/submit/400 paths, the hidden report action on
// the viewer's own profile, and paging. Rating calls are spied on the
// api object (house style: rating.test.tsx).
// Author review: [pending]
// 2026-10-31 (Claude Code, Fable 5), issue #280: the Joined and
// Deliveries-completed tiles — the stubbed fetch serves order-service's
// completed count and a joinedAt; the expected month-year label is
// recomputed with the same Intl formatter (house style:
// credits.test.tsx); tiles survive zero reviews and a failed count.
// Author review: pending.

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ratingApi } from '@/features/rating/api'
import type { ReviewResponse } from '@/features/rating/types'
import { clearUsernameCache } from '@/features/user/usernames'
import { ApiError } from '@/lib/api/http'
import { PublicProfile } from '@/routes/public-profile'

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

const session = vi.hoisted(() => ({ userId: 3 as number | null }))
vi.mock('@/features/order/useCurrentUserId', () => ({
  useCurrentUserId: () => session.userId,
}))

// user-service, as the page's gate fetch and the usernames cache see it
const usernamesById: Record<string, string> = {
  '9': 'courier_maya',
  '3': 'user_abc',
  '4': 'user_def',
}

// every stubbed profile joined at the same instant (#280); the tile's
// label is recomputed with the page's own format, never hardcoded
const joinedAt = '2026-02-10T09:00:00Z'
const joinedLabel = new Intl.DateTimeFormat('en-SG', {
  month: 'long',
  year: 'numeric',
}).format(new Date(joinedAt))

let fetchMock: ReturnType<typeof vi.fn>

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type':
        status < 400 ? 'application/json' : 'application/problem+json',
    },
  })
}

const review = (
  raterId: string,
  stars: number,
  text: string | null,
): ReviewResponse => ({
  raterId,
  stars,
  text,
  createdAt: '2026-10-28T04:00:00Z',
})

const pageOf = (
  content: ReviewResponse[],
  overrides: Partial<{
    page: number
    totalElements: number
    totalPages: number
  }> = {},
) => ({
  content,
  page: 0,
  size: 5,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
  ...overrides,
})

function renderProfile(path = '/users/9') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/users/:id" element={<PublicProfile />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  session.userId = 3
  clearUsernameCache()
  fetchMock = vi.fn(async (rawUrl: RequestInfo | URL) => {
    const url = String(rawUrl)
    if (/\/api\/order\/requests\/completed-count\?courierId=/.test(url)) {
      return jsonResponse(200, { count: 7 })
    }
    const match = url.match(/\/api\/user\/users\/([^/?]+)$/)
    const username = match && usernamesById[match[1]]
    if (username) return jsonResponse(200, { username, joinedAt })
    return jsonResponse(404, {
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: 'No such user',
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  vi.spyOn(ratingApi, 'getSummary').mockResolvedValue({
    average: 4.1666,
    count: 18,
  })
  vi.spyOn(ratingApi, 'listReviews').mockResolvedValue(
    pageOf([review('3', 5, 'Fast and reliable!'), review('4', 4, null)]),
  )
  vi.spyOn(ratingApi, 'submitReport').mockResolvedValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

const reportDialog = () => screen.getByRole('dialog', { name: 'Report User' })

async function openReportDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: /Report User/ }))
  return within(reportDialog())
}

describe('the profile card', () => {
  it('shows the username, the rounded average, the count and the reviews', async () => {
    renderProfile()

    expect(
      await screen.findByRole('heading', { name: 'courier_maya' }),
    ).toBeInTheDocument()
    // the raw mean rounds to one decimal, in the star row and the tile
    expect(await screen.findAllByText('4.2')).toHaveLength(2)
    expect(screen.getByText('(18 reviews)')).toBeInTheDocument()
    expect(
      screen.getByRole('img', { name: '4.2 out of 5 stars' }),
    ).toBeInTheDocument()

    // the reviews, with their stars and the no-text fallback
    expect(await screen.findByText('Fast and reliable!')).toBeInTheDocument()
    expect(screen.getByText('No written review')).toBeInTheDocument()
    expect(
      screen.getByRole('img', { name: '5 out of 5 stars' }),
    ).toBeInTheDocument()

    // rater names hydrate at read time from user-service (D32)
    expect(await screen.findByText('user_abc')).toBeInTheDocument()
    expect(await screen.findByText('user_def')).toBeInTheDocument()

    expect(ratingApi.listReviews).toHaveBeenCalledWith('9', 0, 5)
  })

  it('shows the Joined and Deliveries-completed tiles (#280)', async () => {
    renderProfile()

    expect(await screen.findByText(joinedLabel)).toBeInTheDocument()
    expect(screen.getByText('Joined')).toBeInTheDocument()
    expect(await screen.findByText('7')).toBeInTheDocument()
    expect(screen.getByText('Deliveries completed')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://gw/api/order/requests/completed-count?courierId=9',
      expect.anything(),
    )
  })

  it('keeps the tiles when the completed count cannot be fetched', async () => {
    fetchMock.mockImplementation(async (rawUrl: RequestInfo | URL) => {
      const url = String(rawUrl)
      if (url.includes('/api/order/')) {
        return jsonResponse(500, {
          type: 'about:blank',
          title: 'Internal Server Error',
          status: 500,
          detail: 'order-service is down',
        })
      }
      return jsonResponse(200, { username: 'courier_maya', joinedAt })
    })
    renderProfile()

    // the tile degrades to an em dash; the profile stays up
    expect(await screen.findByText(joinedLabel)).toBeInTheDocument()
    expect(await screen.findByText('—')).toBeInTheDocument()
    expect(screen.getByText('Deliveries completed')).toBeInTheDocument()
    expect(await screen.findAllByText('4.2')).toHaveLength(2)
  })

  it('shows the empty state for a user with no reviews', async () => {
    vi.mocked(ratingApi.getSummary).mockResolvedValue({
      average: null,
      count: 0,
    })
    vi.mocked(ratingApi.listReviews).mockResolvedValue(pageOf([]))
    renderProfile()

    // the summary row and the reviews section each say so; "null" nowhere
    expect(await screen.findByText('No reviews yet')).toBeInTheDocument()
    expect(await screen.findByText('No reviews yet.')).toBeInTheDocument()
    expect(screen.queryByText(/null/)).not.toBeInTheDocument()
    expect(
      screen.queryByRole('navigation', { name: 'Pagination' }),
    ).not.toBeInTheDocument()

    // the tiles still render (#280): the average shows an em dash, the
    // joined date and deliveries count answer regardless of reviews
    expect(await screen.findByText(joinedLabel)).toBeInTheDocument()
    expect(await screen.findByText('7')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('shows "User not found" when user-service answers 404', async () => {
    renderProfile('/users/404404')

    expect(await screen.findByText('User not found.')).toBeInTheDocument()
    expect(screen.queryByText('Recent Reviews')).not.toBeInTheDocument()
    expect(ratingApi.getSummary).not.toHaveBeenCalled()
    // the completed-count fetch is gated the same way (#280)
    expect(
      fetchMock.mock.calls.filter(([url]) =>
        String(url).includes('completed-count'),
      ),
    ).toHaveLength(0)
  })

  it("surfaces the review list's problem detail and keeps the summary", async () => {
    vi.mocked(ratingApi.listReviews).mockRejectedValue(
      new ApiError(500, {
        type: 'about:blank',
        title: 'Internal Server Error',
        status: 500,
        detail: 'ratings are unavailable',
      }),
    )
    renderProfile()

    expect(
      await screen.findByText('ratings are unavailable'),
    ).toBeInTheDocument()
    expect(await screen.findAllByText('4.2')).toHaveLength(2)
  })

  it('pages the reviews', async () => {
    vi.mocked(ratingApi.listReviews).mockResolvedValue(
      pageOf(
        [
          review('3', 5, 'one'),
          review('4', 4, 'two'),
          review('3', 3, 'three'),
          review('4', 2, 'four'),
          review('3', 1, 'five'),
        ],
        { totalElements: 7, totalPages: 2 },
      ),
    )
    renderProfile()
    expect(await screen.findByText('Showing 1-5 of 7 reviews')).toBeVisible()

    vi.mocked(ratingApi.listReviews).mockResolvedValue(
      pageOf([review('3', 5, 'six'), review('4', 4, 'seven')], {
        page: 1,
        totalElements: 7,
        totalPages: 2,
      }),
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    expect(await screen.findByText('Showing 6-7 of 7 reviews')).toBeVisible()
    expect(ratingApi.listReviews).toHaveBeenLastCalledWith('9', 1, 5)
  })
})

describe('the Report User dialog', () => {
  it('requires a reason before posting', async () => {
    const user = userEvent.setup()
    renderProfile()
    const dialog = await openReportDialog(user)

    await user.click(dialog.getByRole('button', { name: 'Report user' }))

    expect(dialog.getByText('Choose a reason.')).toBeInTheDocument()
    expect(ratingApi.submitReport).not.toHaveBeenCalled()
  })

  it('posts the chosen reason with trimmed details and confirms', async () => {
    const user = userEvent.setup()
    renderProfile()
    const dialog = await openReportDialog(user)

    await user.click(dialog.getByRole('radio', { name: 'No-show' }))
    await user.type(dialog.getByLabelText(/Details/), '  Never arrived  ')
    await user.click(dialog.getByRole('button', { name: 'Report user' }))

    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Report User' }),
      ).not.toBeInTheDocument(),
    )
    expect(ratingApi.submitReport).toHaveBeenCalledWith({
      reportedUserId: '9',
      reason: 'NO_SHOW',
      details: 'Never arrived',
    })
    expect(
      screen.getByText('Report submitted. An admin will review it.'),
    ).toBeInTheDocument()
  })

  it('omits blank details from the body', async () => {
    const user = userEvent.setup()
    renderProfile()
    const dialog = await openReportDialog(user)

    await user.click(dialog.getByRole('radio', { name: 'Other' }))
    await user.click(dialog.getByRole('button', { name: 'Report user' }))

    await waitFor(() => expect(ratingApi.submitReport).toHaveBeenCalled())
    expect(ratingApi.submitReport).toHaveBeenCalledWith({
      reportedUserId: '9',
      reason: 'OTHER',
    })
  })

  it("shows the service's problem detail inside the open dialog", async () => {
    vi.mocked(ratingApi.submitReport).mockRejectedValue(
      new ApiError(400, {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        detail: 'You cannot report yourself',
      }),
    )
    const user = userEvent.setup()
    renderProfile()
    const dialog = await openReportDialog(user)

    await user.click(dialog.getByRole('radio', { name: 'Abusive behaviour' }))
    await user.click(dialog.getByRole('button', { name: 'Report user' }))

    expect(
      await dialog.findByText('You cannot report yourself'),
    ).toBeInTheDocument()
    expect(reportDialog()).toBeInTheDocument()
  })

  it("is not offered on the viewer's own profile", async () => {
    session.userId = 9
    renderProfile()

    await screen.findByRole('heading', { name: 'courier_maya' })
    expect(
      screen.queryByRole('button', { name: /Report User/ }),
    ).not.toBeInTheDocument()
  })
})
