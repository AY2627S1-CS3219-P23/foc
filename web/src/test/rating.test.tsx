// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: the Rate Your Experience flow on Request History against spied
// rating/order APIs (house style: orders.test.tsx) — stars required,
// the optional write-up and its 1000-char cap, a submit flipping the
// row without a refetch, the already-rated and not-rateable states,
// problem+json details in the dialog (409), the courier tab's subtitle,
// and the control rendering in both the table and the mobile cards.
// 2026-10-31, PR #278 review (@Sinnez1): the dialog cannot be closed
// mid-submit, a 409 locks it into a close-only state, a 404 is not
// cached (a later visit asks again) while a 403 is; the push-driven
// retry lives in request-refetch.test.tsx.
// Author review: [pending]

import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { orderApi } from '@/features/order/api'
import type { RequestDetail, RequestSummary } from '@/features/order/types'
import { ratingApi } from '@/features/rating/api'
import type { RatingResponse } from '@/features/rating/types'
import { clearMyRatingCache } from '@/features/rating/useMyRating'
import { ApiError } from '@/lib/api/http'
import { RequestHistory } from '@/routes/request-history'

const session = vi.hoisted(() => ({ userId: 3 as number | null }))
vi.mock('@/features/order/useCurrentUserId', () => ({
  useCurrentUserId: () => session.userId,
}))

const completed: RequestSummary = {
  id: 1,
  status: 'COMPLETED',
  supplierName: 'CoffeeBean@Com3',
  dropoffLocation: 'UTown Lobby',
  item: '1x Iced Latte',
  creditAmount: 3,
  deadline: new Date(Date.now() + 3_600_000).toISOString(),
  createdAt: new Date().toISOString(),
  autoCompleteAt: null,
}

const detail: RequestDetail = {
  ...completed,
  requesterId: 3,
  courierId: 9,
  supplierId: 1,
  acceptedAt: null,
  collectedAt: null,
  arrivedAt: null,
}

const myRating = (stars: number): RatingResponse => ({
  id: 'rtg-1',
  requestId: '1',
  raterId: '3',
  rateeId: '9',
  stars,
  text: null,
  createdAt: new Date().toISOString(),
})

const pageOf = (content: RequestSummary[]) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: 1,
})

function renderHistory() {
  return render(
    <MemoryRouter initialEntries={['/history']}>
      <Routes>
        <Route path="/history" element={<RequestHistory />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  session.userId = 3
  clearMyRatingCache()
  vi.spyOn(orderApi, 'listMyRequests').mockResolvedValue(pageOf([completed]))
  vi.spyOn(orderApi, 'getRequest').mockResolvedValue(detail)
  vi.spyOn(orderApi, 'getUsername').mockImplementation(async (id) =>
    id === 3 ? 'carol' : 'courier_maya',
  )
  vi.spyOn(ratingApi, 'getMyRating').mockResolvedValue({ rating: null })
})

afterEach(() => {
  vi.restoreAllMocks()
})

// the dialog, once open
const dialog = () =>
  screen.getByRole('dialog', { name: 'Rate Your Experience' })

async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  // the table and the mobile cards are both in the page under jsdom
  const buttons = await screen.findAllByRole('button', { name: 'Rate' })
  await user.click(buttons[0])
  return within(dialog())
}

describe('the Rate entry point on Request History', () => {
  it('offers Rate on a completed row, in the table and the mobile card', async () => {
    renderHistory()
    expect(await screen.findAllByRole('button', { name: 'Rate' })).toHaveLength(
      2,
    )
    // one lookup despite the two renders — the per-request cache
    expect(ratingApi.getMyRating).toHaveBeenCalledTimes(1)
    expect(ratingApi.getMyRating).toHaveBeenCalledWith(1)
  })

  it('shows the given stars instead once the viewer has rated', async () => {
    vi.mocked(ratingApi.getMyRating).mockResolvedValue({
      rating: myRating(5),
    })
    renderHistory()

    expect(await screen.findAllByLabelText('Rated 5 stars')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'Rate' })).toBeNull()
  })

  it('shows nothing when rating-service says not rateable (404)', async () => {
    vi.mocked(ratingApi.getMyRating).mockRejectedValue(
      new ApiError(404, {
        status: 404,
        detail: 'No completed request with that id',
      }),
    )
    renderHistory()

    await screen.findByRole('table')
    await waitFor(() => expect(ratingApi.getMyRating).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Rate' })).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('does not cache a 404 — the next visit asks again (PR #278 review)', async () => {
    // the GET beat rating-service's request.completed consumer
    vi.mocked(ratingApi.getMyRating)
      .mockRejectedValueOnce(
        new ApiError(404, {
          status: 404,
          detail: 'No completed request with that id',
        }),
      )
      .mockResolvedValue({ rating: null })
    const { unmount } = renderHistory()

    await screen.findByRole('table')
    await waitFor(() => expect(ratingApi.getMyRating).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('button', { name: 'Rate' })).toBeNull()
    unmount()

    // ingestion has caught up by the next mount (a route change here)
    renderHistory()
    expect(await screen.findAllByRole('button', { name: 'Rate' })).toHaveLength(
      2,
    )
    expect(ratingApi.getMyRating).toHaveBeenCalledTimes(2)
  })

  it('keeps the 403 answer for the whole session', async () => {
    vi.mocked(ratingApi.getMyRating).mockRejectedValue(
      new ApiError(403, { status: 403, detail: 'Not a party' }),
    )
    const { unmount } = renderHistory()

    await screen.findByRole('table')
    await waitFor(() => expect(ratingApi.getMyRating).toHaveBeenCalledTimes(1))
    unmount()

    renderHistory()
    await screen.findByRole('table')
    expect(screen.queryByRole('button', { name: 'Rate' })).toBeNull()
    expect(ratingApi.getMyRating).toHaveBeenCalledTimes(1)
  })

  it('asks nothing for rows that did not complete', async () => {
    vi.mocked(orderApi.listMyRequests).mockResolvedValue(
      pageOf([{ ...completed, status: 'CANCELLED' }]),
    )
    renderHistory()

    await screen.findByRole('table')
    expect(ratingApi.getMyRating).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Rate' })).toBeNull()
  })
})

describe('the Rate Your Experience dialog', () => {
  it('requires a star rating before submitting', async () => {
    const user = userEvent.setup()
    vi.spyOn(ratingApi, 'submitRating')
    renderHistory()
    const d = await openDialog(user)

    await user.click(d.getByRole('button', { name: 'Submit Review' }))

    expect(d.getByText('Choose a star rating.')).toBeInTheDocument()
    expect(ratingApi.submitRating).not.toHaveBeenCalled()
  })

  it('submits stars alone, leaving the optional text out', async () => {
    const user = userEvent.setup()
    const submit = vi
      .spyOn(ratingApi, 'submitRating')
      .mockResolvedValue(myRating(4))
    renderHistory()
    const d = await openDialog(user)

    await user.click(d.getByRole('radio', { name: '4 stars' }))
    await user.click(d.getByRole('button', { name: 'Submit Review' }))

    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith({ requestId: '1', stars: 4 }),
    )
  })

  it('caps the write-up at 1000 characters, counted live', async () => {
    const user = userEvent.setup()
    vi.spyOn(ratingApi, 'submitRating')
    renderHistory()
    const d = await openDialog(user)

    expect(d.getByText('0/1000')).toBeInTheDocument()

    await user.click(d.getByRole('radio', { name: '5 stars' }))
    await user.click(d.getByLabelText(/Write a review/))
    await user.paste('x'.repeat(1001))
    expect(d.getByText('1001/1000')).toBeInTheDocument()

    await user.click(d.getByRole('button', { name: 'Submit Review' }))
    expect(
      d.getByText('Keep the review to 1000 characters.'),
    ).toBeInTheDocument()
    expect(ratingApi.submitRating).not.toHaveBeenCalled()
  })

  it('flips the row to the given stars on success, without a refetch', async () => {
    const user = userEvent.setup()
    vi.spyOn(ratingApi, 'submitRating').mockResolvedValue(myRating(4))
    renderHistory()
    const d = await openDialog(user)

    await user.click(d.getByRole('radio', { name: '4 stars' }))
    await user.click(d.getByLabelText(/Write a review/))
    await user.paste('Fast and friendly')
    await user.click(d.getByRole('button', { name: 'Submit Review' }))

    await waitFor(() =>
      expect(ratingApi.submitRating).toHaveBeenCalledWith({
        requestId: '1',
        stars: 4,
        text: 'Fast and friendly',
      }),
    )
    expect(
      screen.queryByRole('dialog', { name: 'Rate Your Experience' }),
    ).toBeNull()
    expect(await screen.findAllByLabelText('Rated 4 stars')).toHaveLength(2)
    expect(
      screen.getByText('Thanks — your rating of request #1 is in.'),
    ).toBeInTheDocument()
    // the row's state came from the submit response, not a second lookup
    expect(ratingApi.getMyRating).toHaveBeenCalledTimes(1)
  })

  it('cannot be closed while the submission is in flight (PR #278 review)', async () => {
    const user = userEvent.setup()
    let resolveSubmit!: (rating: RatingResponse) => void
    vi.spyOn(ratingApi, 'submitRating').mockReturnValue(
      new Promise((resolve) => {
        resolveSubmit = resolve
      }),
    )
    renderHistory()
    const d = await openDialog(user)

    await user.click(d.getByRole('radio', { name: '4 stars' }))
    await user.click(d.getByRole('button', { name: 'Submit Review' }))

    // Cancel is disabled, and the X (and backdrop) are no-ops: a late
    // result must not act on some other dialog opened meanwhile
    expect(d.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.click(d.getByRole('button', { name: 'Close' }))
    expect(dialog()).toBeInTheDocument()

    await act(async () => resolveSubmit(myRating(4)))
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Rate Your Experience' }),
      ).toBeNull(),
    )
    expect(await screen.findAllByLabelText('Rated 4 stars')).toHaveLength(2)
  })

  it("shows a 409's detail, locks the form close-only and refetches the true state", async () => {
    const user = userEvent.setup()
    vi.spyOn(ratingApi, 'submitRating').mockRejectedValue(
      new ApiError(409, {
        status: 409,
        detail: 'You have already rated this request',
      }),
    )
    renderHistory()
    const d = await openDialog(user)
    // the refetch after the 409 finds the rating made elsewhere
    vi.mocked(ratingApi.getMyRating).mockResolvedValue({
      rating: myRating(5),
    })

    await user.click(d.getByRole('radio', { name: '2 stars' }))
    await user.click(d.getByRole('button', { name: 'Submit Review' }))

    expect(
      await d.findByText('You have already rated this request'),
    ).toBeInTheDocument()
    // one submission per party (#256): no retry can succeed, so the
    // form locks and only Close remains (PR #278 review)
    expect(d.queryByRole('button', { name: 'Submit Review' })).toBeNull()
    expect(d.queryByRole('button', { name: 'Cancel' })).toBeNull()
    expect(d.getByRole('radio', { name: '2 stars' })).toBeDisabled()
    expect(d.getByLabelText(/Write a review/)).toBeDisabled()
    await waitFor(() => expect(ratingApi.getMyRating).toHaveBeenCalledTimes(2))
    expect(await screen.findAllByLabelText('Rated 5 stars')).toHaveLength(2)

    // the footer's Close (the X shares the name)
    const closes = d.getAllByRole('button', { name: 'Close' })
    await user.click(closes[closes.length - 1])
    expect(
      screen.queryByRole('dialog', { name: 'Rate Your Experience' }),
    ).toBeNull()
  })

  it("names the courier on the requester tab's dialog", async () => {
    const user = userEvent.setup()
    renderHistory()
    const d = await openDialog(user)

    expect(
      await d.findByText('Request #1 — Delivered by courier_maya'),
    ).toBeInTheDocument()
  })

  it("names the requester on the courier tab's dialog", async () => {
    const user = userEvent.setup()
    renderHistory()
    await screen.findAllByRole('button', { name: 'Rate' })

    await user.click(screen.getByRole('tab', { name: /As Courier/ }))
    const d = await openDialog(user)

    expect(
      await d.findByText('Request #1 — Requested by carol'),
    ).toBeInTheDocument()
  })
})
