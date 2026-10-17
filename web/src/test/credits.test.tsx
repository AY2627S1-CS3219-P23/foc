// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: tests for the live credit balance and the credits page against
// a spied creditApi, on profile.test.tsx's harness (memory router over
// the real route table, a stored session to pass ProtectedRoute): the
// badge and its link, the three cards, the rows' labels and signs, the
// amount and date filters as query parameters (local days to UTC, the
// end exclusive), the client-side range checks, paging, empty and error
// states, and the 30 s poll that pauses in a hidden tab and reloads the
// rows on each poll.
// 2026-10-17: the admin dashboard's Common Pool box — its figures, the
// times in Singapore time, the empty and preview wording, and an error;
// its past-distributions table, newest first, paged, and empty.
// 2026-10-17, issue #207 (PR #195 review nits): loading instead of a
// stale empty state, and a past-distributions retry shown as loading.
// Author review: Ryan Ang, pending pull request review.

import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { creditApi } from '@/features/credit/api'
import { BALANCE_POLL_MS } from '@/features/credit/CreditBalanceProvider'
import { NO_FILTERS, parseFilters } from '@/features/credit/history'
import type {
  CreditBalance,
  CreditHistoryEntry,
  CreditPage,
} from '@/features/credit/types'
import { adminUserApi } from '@/features/user/adminApi'
import { ApiError } from '@/lib/api/http'
import { routes } from '../routes'

// no service URLs: creditApi and adminUserApi are spied below, and with
// no notification URL the shell opens no real WebSocket
vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: '',
    supplier: '',
    order: '',
    credit: '',
    notification: '',
  },
}))

let balance: CreditBalance

function page(
  content: CreditHistoryEntry[],
  extra: Partial<CreditPage<CreditHistoryEntry>> = {},
): CreditPage<CreditHistoryEntry> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    ...extra,
  }
}

const ROWS: CreditHistoryEntry[] = [
  {
    id: 3,
    type: 'TRANSFER_IN',
    amount: 3,
    requestRef: 'r-1042',
    occurredAt: '2026-10-05T03:45:00Z',
  },
  {
    id: 2,
    type: 'RESERVE',
    amount: 5,
    requestRef: 'r-1048',
    occurredAt: '2026-10-05T02:30:00Z',
  },
  {
    id: 1,
    type: 'PROVISION',
    amount: 5,
    requestRef: null,
    occurredAt: '2026-10-01T01:00:00Z',
  },
]

function renderAt(path: string) {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: [path] })}
    />,
  )
}

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => state,
  })
  document.dispatchEvent(new Event('visibilitychange'))
}

// the desktop table; the mobile list renders the same rows (CSS hides
// one or the other, which jsdom does not apply)
const table = () => screen.getByRole('table')

beforeEach(() => {
  vi.restoreAllMocks()
  localStorage.setItem('user', JSON.stringify({ accessToken: 'test-token' }))
  vi.spyOn(adminUserApi, 'getCurrentUser').mockResolvedValue({
    id: 3,
    email: 'e1000101@u.nus.edu',
    username: 'nus_courier_99',
    role: 'USER',
    createdAt: '2026-09-03T08:00:00Z',
  })
  balance = { available: 42, reserved: 8, total: 50 }
  vi.spyOn(creditApi, 'getBalance').mockImplementation(async () => ({
    ...balance,
  }))
  vi.spyOn(creditApi, 'getHistory').mockResolvedValue(page(ROWS))
})

afterEach(() => {
  localStorage.clear()
  setVisibility('visible')
})

describe('credits badge', () => {
  it('shows the available balance and links to the credits page', async () => {
    renderAt('/')
    const badge = await screen.findByRole('link', { name: 'Credits: 42' })
    expect(badge).toHaveAttribute('href', '/credits')
  })

  it('shows a dash while logged out, without asking for a balance', () => {
    localStorage.clear()
    renderAt('/')
    expect(screen.getByRole('link', { name: 'Credits: —' })).toBeInTheDocument()
    expect(creditApi.getBalance).not.toHaveBeenCalled()
  })

  it('shows a dash when the balance cannot be read', async () => {
    vi.mocked(creditApi.getBalance).mockRejectedValue(
      new ApiError(503, { status: 503, detail: 'down' }),
    )
    renderAt('/')
    expect(
      await screen.findByTitle('Credit balance unavailable'),
    ).toHaveTextContent('Credits: —')
  })
})

describe('credits page', () => {
  it('shows the three balances and the history rows with their signs', async () => {
    renderAt('/credits')

    expect(
      await screen.findByRole('heading', { name: 'Credit Transactions' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('42', { selector: 'dd' }),
    ).toBeInTheDocument()
    expect(screen.getByText('8', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('50', { selector: 'dd' })).toBeInTheDocument()

    await waitFor(() => expect(table()).toBeInTheDocument())
    const rows = within(table()).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('Earned')
    expect(rows[0]).toHaveTextContent('Earned: delivered request #r-1042')
    expect(rows[0]).toHaveTextContent('+3')
    expect(rows[1]).toHaveTextContent('Reserved: created request #r-1048')
    expect(rows[1]).toHaveTextContent('−5')
    expect(rows[2]).toHaveTextContent('Initial allocation: account created')
    expect(rows[2]).toHaveTextContent('+5')
    expect(creditApi.getHistory).toHaveBeenCalledWith({ page: 0 })
  })

  it('sends the amount filters as minAmount and maxAmount', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.type(screen.getByLabelText('Min amount'), '2')
    await user.type(screen.getByLabelText('Max amount'), '5')

    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({
        page: 0,
        minAmount: 2,
        maxAmount: 5,
      }),
    )
  })

  it('sends a date range as local days in UTC, the end exclusive', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.type(screen.getByLabelText('From'), '2026-10-01')
    await user.type(screen.getByLabelText('To'), '2026-10-05')

    // the start of 1 October and of 6 October in the browser's zone,
    // so 5 October is included whole
    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({
        page: 0,
        from: new Date(2026, 9, 1).toISOString(),
        to: new Date(2026, 9, 6).toISOString(),
      }),
    )
  })

  it('refuses a minimum above the maximum without asking the server', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')
    const calls = vi.mocked(creditApi.getHistory).mock.calls.length

    await user.type(screen.getByLabelText('Min amount'), '9')
    await user.type(screen.getByLabelText('Max amount'), '3')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The minimum amount cannot be above the maximum.',
    )
    // only the keystrokes before the range went bad reached the server
    const after = vi.mocked(creditApi.getHistory).mock.calls.slice(calls)
    expect(after).not.toContainEqual([{ page: 0, minAmount: 9, maxAmount: 3 }])
  })

  it('refuses a start date after the end date', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.type(screen.getByLabelText('From'), '2026-10-09')
    await user.type(screen.getByLabelText('To'), '2026-10-02')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The start date cannot be after the end date.',
    )
  })

  it('clears the filters back to the unfiltered first page', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.type(screen.getByLabelText('Min amount'), '4')
    await user.click(screen.getByRole('button', { name: 'Clear Filters' }))

    expect(screen.getByLabelText('Min amount')).toHaveValue(null)
    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({ page: 0 }),
    )
  })

  it('pages through the history', async () => {
    vi.mocked(creditApi.getHistory).mockImplementation(async (q) =>
      page(ROWS, { page: q?.page ?? 0, totalPages: 3, totalElements: 60 }),
    )
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({ page: 1 }),
    )
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute(
        'aria-current',
        'page',
      ),
    )
  })

  it('says when there is no history yet', async () => {
    vi.mocked(creditApi.getHistory).mockResolvedValue(page([]))
    renderAt('/credits')
    expect(await screen.findByText('No transactions yet.')).toBeInTheDocument()
  })

  it('says when no row matches the filters', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')
    vi.mocked(creditApi.getHistory).mockResolvedValue(page([]))

    await user.type(screen.getByLabelText('Min amount'), '99')

    expect(
      await screen.findByText('No transactions match these filters.'),
    ).toBeInTheDocument()
  })

  it('waits for typing to pause before asking the server', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')

    await user.type(screen.getByLabelText('Min amount'), '100')

    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({
        page: 0,
        minAmount: 100,
      }),
    )
    // one request for the whole number, none for 1 or 10
    const asked = vi
      .mocked(creditApi.getHistory)
      .mock.calls.map(([q]) => q?.minAmount)
    expect(asked).not.toContain(1)
    expect(asked).not.toContain(10)
  })

  it('shows loading, not a stale empty state, while the next query runs', async () => {
    const user = userEvent.setup()
    renderAt('/credits')
    await screen.findByRole('table')
    // a filtered query finds nothing; the unfiltered one never answers
    vi.mocked(creditApi.getHistory).mockImplementation((q) =>
      q?.minAmount ? Promise.resolve(page([])) : new Promise(() => {}),
    )

    await user.type(screen.getByLabelText('Min amount'), '99')
    await screen.findByText('No transactions match these filters.')
    await user.click(screen.getByRole('button', { name: 'Clear Filters' }))

    // the filtered result's empty message must not stand in for the
    // unfiltered answer, and neither may "No transactions yet."
    await waitFor(() =>
      expect(creditApi.getHistory).toHaveBeenLastCalledWith({ page: 0 }),
    )
    expect(screen.getByText('Loading transactions…')).toBeInTheDocument()
    expect(
      screen.queryByText('No transactions match these filters.'),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('No transactions yet.')).not.toBeInTheDocument()
  })

  it("shows the server's reason when the history fails to load", async () => {
    vi.mocked(creditApi.getHistory).mockRejectedValue(
      new ApiError(400, { status: 400, detail: 'size must be at most 100' }),
    )
    renderAt('/credits')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'size must be at most 100',
    )
  })

  it('needs a session', async () => {
    localStorage.clear()
    renderAt('/credits')
    expect(
      await screen.findByRole('heading', { name: 'Log In' }),
    ).toBeInTheDocument()
  })
})

describe('date filters', () => {
  // the start of a local day, built as the page does
  const localDay = (year: number, month: number, day: number) => {
    const d = new Date(0)
    d.setFullYear(year, month - 1, day)
    d.setHours(0, 0, 0, 0)
    return d.toISOString()
  }

  it('keeps a two-digit year as typed, not 19xx', () => {
    const parsed = parseFilters({ ...NO_FILTERS, from: '0026-10-01' })
    expect(parsed).toEqual({
      ok: true,
      query: { from: localDay(26, 10, 1) },
    })
  })

  it('ends a range on 31 December at the start of the next year', () => {
    const parsed = parseFilters({ ...NO_FILTERS, to: '2026-12-31' })
    expect(parsed).toEqual({ ok: true, query: { to: localDay(2027, 1, 1) } })
  })

  it('refuses a value that is not a date instead of throwing', () => {
    for (const from of ['2026-10', 'not a date', '2026-02-30']) {
      expect(parseFilters({ ...NO_FILTERS, from })).toEqual({
        ok: false,
        error: 'Dates must be valid calendar dates.',
      })
    }
  })
})

describe('profile card', () => {
  it('shows the live balance with a link to the credits page', async () => {
    renderAt('/profile')
    expect(
      await screen.findByText('Available: 42 | Reserved: 8'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'View credit transactions' }),
    ).toHaveAttribute('href', '/credits')
  })
})

describe('admin common pool box', () => {
  beforeEach(() => {
    // AdminRoute lets ADMIN and OWNER through, from GET /users/me
    vi.mocked(adminUserApi.getCurrentUser).mockResolvedValue({
      id: 1,
      email: 'e0000001@u.nus.edu',
      username: 'bee_lim',
      role: 'ADMIN',
      createdAt: '2026-09-03T08:00:00Z',
    })
    vi.spyOn(adminUserApi, 'listUsers').mockResolvedValue({
      content: [],
      page: { size: 10, number: 0, totalElements: 0, totalPages: 0 },
    })
    vi.spyOn(creditApi, 'getRedistributions').mockResolvedValue({
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
  })

  const pastRuns = async () =>
    within(await screen.findByRole('group', { name: 'Past distributions' }))

  const poolBox = async () =>
    within(await screen.findByRole('region', { name: 'Common Pool' }))

  it('shows the pool, the share preview and both runs in Singapore time', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 37,
      accounts: 10,
      share: 3,
      remainder: 7,
      lastRun: {
        period: '2026-10',
        distributed: 12,
        remainder: 2,
        ranAt: '2026-09-30T16:00:00Z',
      },
      nextRunAt: '2026-10-31T16:00:00Z',
    })
    renderAt('/admin')
    const box = await poolBox()

    expect(await box.findByText('37 credits')).toBeInTheDocument()
    expect(box.getByText('10')).toBeInTheDocument()
    expect(
      box.getByText('If shared now: 3 credits each, 1 more to 7 of them'),
    ).toBeInTheDocument()
    expect(box.getByText('October 2026')).toBeInTheDocument()
    // 16:00 UTC is midnight in Singapore, whatever the test machine's zone
    expect(
      box.getByText(/12 credits shared on 1 Oct 2026.*12:00.*SGT/),
    ).toBeInTheDocument()
    expect(box.getByText(/1 Nov 2026.*12:00.*SGT/)).toBeInTheDocument()
  })

  it('says when nothing has been distributed and no account exists', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 1,
      accounts: 0,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    renderAt('/admin')
    const box = await poolBox()

    expect(await box.findByText('1 credit')).toBeInTheDocument()
    expect(box.getByText('None yet')).toBeInTheDocument()
    expect(
      box.getByText('If shared now: No accounts to share among yet'),
    ).toBeInTheDocument()
  })

  it('lists past distributions newest first, in Singapore time', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 0,
      accounts: 2,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    vi.mocked(creditApi.getRedistributions).mockResolvedValue({
      content: [
        {
          period: '2026-10',
          distributed: 12,
          remainder: 2,
          ranAt: '2026-09-30T16:00:00Z',
        },
        {
          period: '2026-09',
          distributed: 1,
          remainder: 1,
          ranAt: '2026-08-31T16:00:00Z',
        },
      ],
      page: 0,
      size: 12,
      totalElements: 2,
      totalPages: 1,
    })
    renderAt('/admin')
    const table = await (await pastRuns()).findByRole('table')

    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('October 2026')
    expect(rows[0]).toHaveTextContent('12 credits')
    expect(rows[0]).toHaveTextContent(/1 Oct 2026.*12:00.*SGT/)
    expect(rows[1]).toHaveTextContent('September 2026')
    expect(rows[1]).toHaveTextContent('1 credit')
    expect(creditApi.getRedistributions).toHaveBeenCalledWith({
      page: 0,
      size: 12,
    })
  })

  it('pages through past distributions', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 0,
      accounts: 2,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    vi.mocked(creditApi.getRedistributions).mockImplementation(async (q) => ({
      content: [
        {
          period: q?.page === 1 ? '2025-10' : '2026-10',
          distributed: 3,
          remainder: 0,
          ranAt: '2026-09-30T16:00:00Z',
        },
      ],
      page: q?.page ?? 0,
      size: 12,
      totalElements: 13,
      totalPages: 2,
    }))
    const user = userEvent.setup()
    renderAt('/admin')
    const past = await pastRuns()
    await past.findByText('October 2026')

    await user.click(past.getByRole('button', { name: 'Page 2' }))

    expect(await past.findByText('October 2025')).toBeInTheDocument()
    expect(creditApi.getRedistributions).toHaveBeenLastCalledWith({
      page: 1,
      size: 12,
    })
  })

  it('keeps the last page up after a failed one, and can try again', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 0,
      accounts: 2,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    let failSecondPage = true
    vi.mocked(creditApi.getRedistributions).mockImplementation(async (q) => {
      if (q?.page === 1 && failSecondPage) {
        throw new ApiError(500, { status: 500, detail: 'Server error' })
      }
      return {
        content: [
          {
            period: q?.page === 1 ? '2025-10' : '2026-10',
            distributed: 3,
            remainder: 0,
            ranAt: '2026-09-30T16:00:00Z',
          },
        ],
        page: q?.page ?? 0,
        size: 12,
        totalElements: 13,
        totalPages: 2,
      }
    })
    const user = userEvent.setup()
    renderAt('/admin')
    const past = await pastRuns()
    await past.findByText('October 2026')

    await user.click(past.getByRole('button', { name: 'Page 2' }))

    expect(await past.findByRole('alert')).toHaveTextContent('Server error')
    // the first page and the pager are still there
    expect(past.getByText('October 2026')).toBeInTheDocument()
    expect(past.getByRole('button', { name: 'Page 2' })).toBeInTheDocument()

    failSecondPage = false
    await user.click(past.getByRole('button', { name: 'Try again' }))

    expect(await past.findByText('October 2025')).toBeInTheDocument()
    expect(past.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows a retry as loading until it answers', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 0,
      accounts: 2,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    vi.mocked(creditApi.getRedistributions).mockRejectedValue(
      new ApiError(500, { status: 500, detail: 'Server error' }),
    )
    const user = userEvent.setup()
    renderAt('/admin')
    const past = await pastRuns()
    await past.findByRole('alert')

    // the retry never answers, so it stays in flight
    vi.mocked(creditApi.getRedistributions).mockImplementation(
      () => new Promise(() => {}),
    )
    await user.click(past.getByRole('button', { name: 'Try again' }))

    expect(past.getByText('Loading…')).toBeInTheDocument()
    expect(past.queryByRole('alert')).not.toBeInTheDocument()
    expect(
      screen.getByRole('group', { name: 'Past distributions' }),
    ).toHaveAttribute('aria-busy', 'true')
  })

  it('says when nothing has been distributed yet', async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
      poolBalance: 0,
      accounts: 0,
      share: 0,
      remainder: 0,
      lastRun: null,
      nextRunAt: '2026-10-20T16:00:00Z',
    })
    renderAt('/admin')
    expect(
      await (await pastRuns()).findByText('No distributions yet.'),
    ).toBeInTheDocument()
  })

  it("shows the server's reason when the pool cannot be read", async () => {
    vi.spyOn(creditApi, 'getPoolSummary').mockRejectedValue(
      new ApiError(403, {
        status: 403,
        detail: 'You do not have permission to perform this action',
      }),
    )
    renderAt('/admin')
    const box = await poolBox()

    expect(await box.findByRole('alert')).toHaveTextContent(
      'You do not have permission to perform this action',
    )
  })
})

describe('balance poll', () => {
  beforeEach(() => {
    // intervals only, so testing-library's own timeouts stay real
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('refetches every 30 s and shows the new balance', async () => {
    renderAt('/')
    await screen.findByRole('link', { name: 'Credits: 42' })

    balance = { available: 45, reserved: 8, total: 53 }
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS))

    expect(
      await screen.findByRole('link', { name: 'Credits: 45' }),
    ).toBeInTheDocument()
    expect(creditApi.getBalance).toHaveBeenCalledTimes(2)
  })

  it('pauses while the tab is hidden and refetches when it is shown', async () => {
    renderAt('/')
    await screen.findByRole('link', { name: 'Credits: 42' })

    act(() => setVisibility('hidden'))
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS * 3))
    expect(creditApi.getBalance).toHaveBeenCalledTimes(1)

    balance = { available: 40, reserved: 10, total: 50 }
    act(() => setVisibility('visible'))

    expect(
      await screen.findByRole('link', { name: 'Credits: 40' }),
    ).toBeInTheDocument()
    expect(creditApi.getBalance).toHaveBeenCalledTimes(2)
  })

  it('keeps the last balance when a poll fails', async () => {
    renderAt('/')
    await screen.findByRole('link', { name: 'Credits: 42' })

    vi.mocked(creditApi.getBalance).mockRejectedValue(new TypeError('offline'))
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS))

    await waitFor(() => expect(creditApi.getBalance).toHaveBeenCalledTimes(2))
    expect(
      screen.getByRole('link', { name: 'Credits: 42' }),
    ).toBeInTheDocument()
  })

  it('reloads the history rows on every poll, the balance changed or not', async () => {
    renderAt('/credits')
    await screen.findByRole('table')
    await waitFor(() => expect(creditApi.getHistory).toHaveBeenCalledTimes(1))

    // unchanged: a reserve and a release between polls add rows too
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS))
    await waitFor(() => expect(creditApi.getHistory).toHaveBeenCalledTimes(2))

    balance = { available: 45, reserved: 8, total: 53 }
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS))
    await waitFor(() => expect(creditApi.getHistory).toHaveBeenCalledTimes(3))
  })

  it('never lets an older balance answer replace a newer one', async () => {
    renderAt('/')
    await screen.findByRole('link', { name: 'Credits: 42' })

    // the interval's poll is slow; showing the tab again starts another
    let answerSlow: (b: CreditBalance) => void = () => {}
    vi.mocked(creditApi.getBalance)
      .mockImplementationOnce(
        () => new Promise((resolve) => (answerSlow = resolve)),
      )
      .mockImplementationOnce(async () => ({
        available: 45,
        reserved: 8,
        total: 53,
      }))
    await act(async () => vi.advanceTimersByTime(BALANCE_POLL_MS))
    act(() => setVisibility('hidden'))
    act(() => setVisibility('visible'))
    await screen.findByRole('link', { name: 'Credits: 45' })

    // the slow, older answer arrives last
    await act(async () => answerSlow({ available: 40, reserved: 8, total: 48 }))

    expect(
      screen.getByRole('link', { name: 'Credits: 45' }),
    ).toBeInTheDocument()
  })
})
