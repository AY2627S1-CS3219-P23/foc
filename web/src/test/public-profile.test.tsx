// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the public profile page — summary and reviews rendering with
// rater names hydrating through the real usernames cache (stubbed
// fetch serves user-service; house style: usernames.test.ts), the
// no-reviews empty state, the user-not-found gate, the Report User
// dialog's validation/submit/400 paths, the hidden report action on
// the viewer's own profile, and paging. Rating calls are spied on the
// api object (house style: rating.test.tsx).
// 2026-10-31, Claude Code (Fable 5), PR #279 review (@Sinnez1): added
// the non-canonical-id gate ('me', '09'), 400-as-not-found, the Try
// again paths for the gate/summary/reviews, the last-good-page staying
// up through a pending or failed page load with the pager disabled,
// the dialog refusing to close mid-submit, rater names staying blank
// until the lookup settles (Unknown user only on a confirmed 404), and
// the trailing zero on a whole average.
// Author review: [pending]

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

const problem = (status: number, title: string, detail: string) => ({
  type: 'about:blank',
  title,
  status,
  detail,
})

// the stubbed user-service: a username for known ids, 404 otherwise;
// tests that need one id to behave differently wrap this default
function userService(rawUrl: RequestInfo | URL): Response {
  const match = String(rawUrl).match(/\/api\/user\/users\/([^/?]+)$/)
  const username = match && usernamesById[match[1]]
  if (username) return jsonResponse(200, { username })
  return jsonResponse(404, problem(404, 'Not Found', 'No such user'))
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
  fetchMock = vi.fn(async (rawUrl: RequestInfo | URL) => userService(rawUrl))
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
  })

  it('shows "User not found" when user-service answers 404', async () => {
    renderProfile('/users/404404')

    expect(await screen.findByText('User not found.')).toBeInTheDocument()
    expect(screen.queryByText('Recent Reviews')).not.toBeInTheDocument()
    expect(ratingApi.getSummary).not.toHaveBeenCalled()
  })

  // user-service would canonicalize these (`me` → the viewer, `09` → 9)
  // while rating-service stores the raw string, so a report filed under
  // them dodges the self-report check (PR #279 review)
  it.each(['me', '09', 'abc', '-1'])(
    'rejects the non-canonical id %j without calling any service',
    async (id) => {
      renderProfile(`/users/${id}`)

      expect(await screen.findByText('User not found.')).toBeInTheDocument()
      expect(fetchMock).not.toHaveBeenCalled()
      expect(ratingApi.getSummary).not.toHaveBeenCalled()
      expect(
        screen.queryByRole('button', { name: /Report User/ }),
      ).not.toBeInTheDocument()
    },
  )

  it('treats a 400 from user-service as user not found, not retryable', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, problem(400, 'Bad Request', 'id must be a number')),
    )
    renderProfile('/users/123')

    expect(await screen.findByText('User not found.')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Try again' }),
    ).not.toBeInTheDocument()
  })

  it('offers a retry when the profile gate fails transiently', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(502, problem(502, 'Bad Gateway', 'user-service is down')),
    )
    renderProfile()

    expect(await screen.findByText('user-service is down')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(
      await screen.findByRole('heading', { name: 'courier_maya' }),
    ).toBeInTheDocument()
  })

  it('offers a retry when the summary fails', async () => {
    vi.mocked(ratingApi.getSummary).mockRejectedValueOnce(
      new ApiError(502, problem(502, 'Bad Gateway', 'summary is down')),
    )
    renderProfile()

    expect(await screen.findByText('summary is down')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findAllByText('4.2')).toHaveLength(2)
  })

  it("surfaces the review list's problem detail, keeps the summary and retries", async () => {
    vi.mocked(ratingApi.listReviews).mockRejectedValueOnce(
      new ApiError(
        500,
        problem(500, 'Internal Server Error', 'ratings are unavailable'),
      ),
    )
    renderProfile()

    expect(
      await screen.findByText(/ratings are unavailable/),
    ).toBeInTheDocument()
    expect(await screen.findAllByText('4.2')).toHaveLength(2)

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Fast and reliable!')).toBeInTheDocument()
  })

  const firstPage = pageOf(
    [
      review('3', 5, 'one'),
      review('4', 4, 'two'),
      review('3', 3, 'three'),
      review('4', 2, 'four'),
      review('3', 1, 'five'),
    ],
    { totalElements: 7, totalPages: 2 },
  )
  const secondPage = pageOf([review('3', 5, 'six'), review('4', 4, 'seven')], {
    page: 1,
    totalElements: 7,
    totalPages: 2,
  })

  it('pages the reviews, keeping the answered page up while the next loads', async () => {
    vi.mocked(ratingApi.listReviews).mockResolvedValueOnce(firstPage)
    renderProfile()
    expect(await screen.findByText('Showing 1-5 of 7 reviews')).toBeVisible()

    let answer!: (page: typeof secondPage) => void
    vi.mocked(ratingApi.listReviews).mockImplementationOnce(
      () => new Promise((resolve) => (answer = resolve)),
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    // the range and rows still describe the answered page, and the pager
    // is disabled, until the new page lands
    expect(screen.getByText('Showing 1-5 of 7 reviews')).toBeVisible()
    expect(screen.getByText('one')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Page 2' })).toBeDisabled()

    answer(secondPage)
    expect(await screen.findByText('Showing 6-7 of 7 reviews')).toBeVisible()
    expect(screen.getByText('six')).toBeVisible()
    expect(ratingApi.listReviews).toHaveBeenLastCalledWith('9', 1, 5)
  })

  it('keeps the loaded page and pager up when the next page fails', async () => {
    vi.mocked(ratingApi.listReviews)
      .mockResolvedValueOnce(firstPage)
      .mockRejectedValueOnce(
        new ApiError(502, problem(502, 'Bad Gateway', 'ratings are down')),
      )
      .mockResolvedValueOnce(secondPage)
    renderProfile()
    expect(await screen.findByText('Showing 1-5 of 7 reviews')).toBeVisible()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    // the failure shows above the page already loaded, which stays usable
    expect(await screen.findByText(/ratings are down/)).toBeInTheDocument()
    expect(screen.getByText('Showing 1-5 of 7 reviews')).toBeVisible()
    expect(screen.getByText('one')).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Showing 6-7 of 7 reviews')).toBeVisible()
    expect(screen.queryByText(/ratings are down/)).not.toBeInTheDocument()
  })

  it('keeps the trailing zero on a whole average', async () => {
    vi.mocked(ratingApi.getSummary).mockResolvedValue({ average: 5, count: 3 })
    renderProfile()

    expect(await screen.findAllByText('5.0')).toHaveLength(2)
  })

  it('wraps review text and keeps its line breaks', async () => {
    renderProfile()

    expect(await screen.findByText('Fast and reliable!')).toHaveClass(
      'break-words',
      'whitespace-pre-line',
    )
  })

  it('labels a rater Unknown user only once user-service confirms the 404', async () => {
    vi.mocked(ratingApi.listReviews).mockResolvedValue(
      pageOf([review('777', 4, 'ghost'), review('666', 3, 'flaky')]),
    )
    let answer!: (r: Response) => void
    fetchMock.mockImplementation(async (rawUrl: RequestInfo | URL) => {
      const id = String(rawUrl).match(/\/api\/user\/users\/([^/?]+)$/)?.[1]
      // 777: still resolving; 666: transient 5xx — neither is a 404 yet
      if (id === '777')
        return new Promise<Response>((resolve) => (answer = resolve))
      if (id === '666')
        return jsonResponse(502, problem(502, 'Bad Gateway', 'flaked'))
      return userService(rawUrl)
    })
    renderProfile()

    expect(await screen.findByText('ghost')).toBeInTheDocument()
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/users/666')),
      ).toBe(true),
    )
    // neither the pending lookup nor the transient failure reads as gone
    expect(screen.queryByText('Unknown user')).not.toBeInTheDocument()

    answer(jsonResponse(404, problem(404, 'Not Found', 'No such user')))
    expect(await screen.findAllByText('Unknown user')).toHaveLength(1)
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

  it('cannot be closed while the report is in flight', async () => {
    let answer!: () => void
    vi.mocked(ratingApi.submitReport).mockImplementation(
      () => new Promise((resolve) => (answer = () => resolve(undefined))),
    )
    const user = userEvent.setup()
    renderProfile()
    const dialog = await openReportDialog(user)

    await user.click(dialog.getByRole('radio', { name: 'No-show' }))
    await user.click(dialog.getByRole('button', { name: 'Report user' }))

    // Cancel is disabled; the X (and the backdrop it shares a handler
    // with) is ignored — a close now could not abort the POST, only
    // orphan its outcome
    expect(dialog.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.click(dialog.getByRole('button', { name: 'Close' }))
    expect(reportDialog()).toBeInTheDocument()

    answer()
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Report User' }),
      ).not.toBeInTheDocument(),
    )
    expect(
      screen.getByText('Report submitted. An admin will review it.'),
    ).toBeInTheDocument()
    expect(ratingApi.submitReport).toHaveBeenCalledTimes(1)
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
