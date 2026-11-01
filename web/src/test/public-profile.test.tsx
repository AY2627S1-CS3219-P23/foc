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
// 2026-10-31 (Claude Code, Fable 5), issue #280: the Joined and
// Deliveries-completed tiles — the stubbed fetch serves order-service's
// completed count and a joinedAt; the expected month-year label is
// recomputed with the same Intl formatter (house style:
// credits.test.tsx); tiles survive zero reviews and a failed count.
// Author review: pending.
// 2026-11-01 (Claude Code, Opus 5), PR #282 review (@Sinnez1): the
// tile assertions are scoped to their own tile (a bare '—' was matching
// the Avg tile's placeholder and passed even with the count's failure
// handler removed), plus the count's own Try again, the summary's Try
// again re-running the count, a missing/unparseable joinedAt, and the
// Avg tile's loading ellipsis. The count is stubbed on orderApi's
// method where the assertion is about the tile, and on fetch where it
// is about the URL.
// Author review: pending.

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import { ratingApi } from '@/features/rating/api'
import type {
  RatingSummaryResponse,
  ReviewResponse,
} from '@/features/rating/types'
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
// label is recomputed with the page's own format, never hardcoded —
// short month, Singapore time (PR #282 review)
const joinedAt = '2026-02-10T09:00:00Z'
const joinedFormat = new Intl.DateTimeFormat('en-SG', {
  timeZone: 'Asia/Singapore',
  month: 'short',
  year: 'numeric',
})
const joinedLabel = joinedFormat.format(new Date(joinedAt))

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

// the stubbed user-service: a username and joinedAt for known ids, 404
// otherwise; tests that need one id to behave differently wrap this
function userService(rawUrl: RequestInfo | URL): Response {
  const match = String(rawUrl).match(/\/api\/user\/users\/([^/?]+)$/)
  const username = match && usernamesById[match[1]]
  if (username) return jsonResponse(200, { username, joinedAt })
  return jsonResponse(404, problem(404, 'Not Found', 'No such user'))
}

// order-service's completed-deliveries count (#280); null for any other
// URL, so the default stub can fall through to user-service
function completedCount(rawUrl: RequestInfo | URL): Response | null {
  return /\/api\/order\/requests\/completed-count\?courierId=/.test(
    String(rawUrl),
  )
    ? jsonResponse(200, { count: 7 })
    : null
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
  fetchMock = vi.fn(
    async (rawUrl: RequestInfo | URL) =>
      completedCount(rawUrl) ?? userService(rawUrl),
  )
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

// one stat tile, by its label — the tiles share placeholders ('…', '—'),
// so every tile assertion has to be scoped to its own (PR #282 review)
async function tile(label: string) {
  const labelled = (await screen.findByText(label)).parentElement
  if (labelled === null) throw new Error(`no tile for ${label}`)
  return within(labelled)
}

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

    expect((await tile('Joined')).getByText(joinedLabel)).toBeInTheDocument()
    expect(
      await (await tile('Deliveries completed')).findByText('7'),
    ).toBeInTheDocument()
    // through orderApi, so VITE_MOCK_ORDER_API covers it, at the gateway
    // path the service answers on (PR #282 review)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://gw/api/order/requests/completed-count?courierId=9',
      expect.anything(),
    )
  })

  it('degrades the count tile and retries it on its own', async () => {
    let answer!: (count: number) => void
    const count = vi
      .spyOn(orderApi, 'getCompletedCount')
      .mockRejectedValueOnce(
        new ApiError(
          500,
          problem(500, 'Internal Server Error', 'order-service is down'),
        ),
      )
      .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    renderProfile()

    // only this tile degrades, and its own Try again is the page's only
    // retry when nothing else failed (PR #282 review)
    const deliveries = await tile('Deliveries completed')
    const retry = await deliveries.findByRole('button', { name: 'Try again' })
    expect(await screen.findAllByText('4.2')).toHaveLength(2)
    expect((await tile('Joined')).getByText(joinedLabel)).toBeInTheDocument()

    await userEvent.setup().click(retry)

    // the retry clears the failure, so the tile loads rather than keeping
    // a stale Try again up while the second call is in flight
    expect(await deliveries.findByText('…')).toBeInTheDocument()

    answer(7)

    expect(await deliveries.findByText('7')).toBeInTheDocument()
    expect(count).toHaveBeenCalledTimes(2)
    expect(count).toHaveBeenLastCalledWith(9)
  })

  it("re-runs the count from the summary's Try again", async () => {
    vi.mocked(ratingApi.getSummary).mockRejectedValueOnce(
      new ApiError(502, problem(502, 'Bad Gateway', 'summary is down')),
    )
    vi.spyOn(orderApi, 'getCompletedCount')
      .mockRejectedValueOnce(
        new ApiError(502, problem(502, 'Bad Gateway', 'order-service is down')),
      )
      .mockResolvedValueOnce(7)
    renderProfile()

    // one transient failure usually hit both, so the summary's retry
    // covers the count as well (PR #282 review)
    expect(await screen.findByText('summary is down')).toBeInTheDocument()
    await userEvent
      .setup()
      .click(within(screen.getByRole('alert')).getByRole('button'))

    expect(await screen.findAllByText('4.2')).toHaveLength(2)
    expect(
      await (await tile('Deliveries completed')).findByText('7'),
    ).toBeInTheDocument()
  })

  it.each([
    ['no joinedAt', {}],
    ['a null joinedAt', { joinedAt: null }],
    ['an unparseable joinedAt', { joinedAt: 'nonsense' }],
  ])('falls back to an em dash for %s', async (_what, profile) => {
    fetchMock.mockImplementation(
      async (rawUrl: RequestInfo | URL) =>
        completedCount(rawUrl) ??
        jsonResponse(200, { username: 'courier_maya', ...profile }),
    )
    renderProfile()

    // the page renders instead of throwing RangeError out of render, and
    // never dates the account to the epoch (PR #282 review)
    expect(
      await screen.findByRole('heading', { name: 'courier_maya' }),
    ).toBeInTheDocument()
    expect((await tile('Joined')).getByText('—')).toBeInTheDocument()
    expect(screen.queryByText(/1970/)).not.toBeInTheDocument()
    expect(
      await (await tile('Deliveries completed')).findByText('7'),
    ).toBeInTheDocument()
  })

  it('formats the Joined month in Singapore time (PR #282 review)', async () => {
    // 31 Jan 20:00 UTC is already February in Singapore, so this instant
    // reads differently by zone — the guard below keeps the test honest
    const straddling = '2026-01-31T20:00:00Z'
    expect(
      new Intl.DateTimeFormat('en-SG', {
        timeZone: 'UTC',
        month: 'short',
        year: 'numeric',
      }).format(new Date(straddling)),
    ).toBe('Jan 2026')
    fetchMock.mockImplementation(
      async (rawUrl: RequestInfo | URL) =>
        completedCount(rawUrl) ??
        jsonResponse(200, { username: 'courier_maya', joinedAt: straddling }),
    )
    renderProfile()

    // one profile reads the same for every viewer (poolFormat's formatSgt)
    expect((await tile('Joined')).getByText('Feb 2026')).toBeInTheDocument()
  })

  it('shows the Avg tile ellipsis until the summary answers', async () => {
    let answer!: (summary: RatingSummaryResponse) => void
    vi.mocked(ratingApi.getSummary).mockReturnValue(
      new Promise((resolve) => (answer = resolve)),
    )
    renderProfile()

    // an em dash is the answer "no rating", not "still loading" — the
    // count tile next to it reads the same way (PR #282 review)
    const avg = await tile('Avg rating received')
    expect(avg.getByText('…')).toBeInTheDocument()

    answer({ average: null, count: 0 })

    expect(await avg.findByText('—')).toBeInTheDocument()
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
    expect((await tile('Joined')).getByText(joinedLabel)).toBeInTheDocument()
    expect(
      await (await tile('Deliveries completed')).findByText('7'),
    ).toBeInTheDocument()
    expect(
      (await tile('Avg rating received')).getByText('—'),
    ).toBeInTheDocument()
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
