// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
// Scope: tests for the account-recovery flow, in auth.test.tsx's
// harness. Pins: a login whose response carries deletionScheduledFor
// lands on the recovery screen (and GET /users/me is NOT asked — it
// answers 404 for a deleted account); Restore posts
// POST /users/me/recover and the stored session sheds the flag; Keep
// Deleted logs out (best-effort POST /auth/logout, session cleared); a
// reloaded flagged session is held on the recovery screen wherever it
// tries to go; an active session that wanders here is sent home.
// Author review: Leong Wei Zhi to review via the PR.

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { routes } from '../routes'

// user-service's URL is empty in tests; give apiFetch one to call
vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://user.test',
    supplier: '',
    order: '',
    credit: '',
    notification: '',
  },
}))

const fetchMock = vi.fn()

function reply(status: number, body: unknown) {
  return {
    ok: status < 400,
    status,
    headers: new Headers(),
    json: async () =>
      body === undefined ? Promise.reject(new SyntaxError('no body')) : body,
  }
}

beforeEach(() => {
  localStorage.clear()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

const deletedSession = {
  accessToken: 'jwt',
  tokenType: 'Bearer',
  expiresIn: 3600,
  deletionScheduledFor: '2026-10-15T03:00:00Z',
}

const restoredAccount = {
  id: 1,
  email: 'e1234567@u.nus.edu',
  username: 'student_alex',
  role: 'USER',
  createdAt: '2026-09-01T00:00:00Z',
}

function renderAt(path: string) {
  const memoryRouter = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={memoryRouter} />)
  return memoryRouter
}

// the url and method of every request fetch saw
function sentRequests() {
  return fetchMock.mock.calls.map(([url, init]) => ({
    url: String(url),
    method: (init as RequestInit).method ?? 'GET',
  }))
}

test('a deleted account’s login lands on the recovery screen, and /users/me is not asked', async () => {
  fetchMock.mockResolvedValueOnce(reply(200, deletedSession))
  const user = userEvent.setup()
  renderAt('/login')

  await user.type(screen.getByLabelText('Username or Email'), 'student_alex')
  await user.type(screen.getByLabelText('Password'), 'OldPassword123')
  const form = screen.getByLabelText('Username or Email').closest('form')!
  await user.click(within(form).getByRole('button', { name: 'Log In' }))

  await waitFor(() =>
    expect(
      screen.getByRole('heading', { name: 'Account Scheduled for Deletion' }),
    ).toBeInTheDocument(),
  )
  // the wireframe's purge date, from the login response — formatted the
  // way the page formats it, so the test is locale-independent
  const purgeDate = new Date(
    deletedSession.deletionScheduledFor,
  ).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
  expect(screen.getByText(purgeDate)).toBeInTheDocument()
  // only the login call went out: GET /users/me would 404 while deleted
  expect(sentRequests()).toEqual([
    { url: 'http://user.test/auth/login', method: 'POST' },
  ])
})

test('Restore posts /users/me/recover, sheds the flag and lands home', async () => {
  localStorage.setItem('user', JSON.stringify(deletedSession))
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    if (String(url).endsWith('/users/me/recover')) {
      return Promise.resolve(reply(200, restoredAccount))
    }
    // the me-fetch that fires once the flag is gone
    if (
      String(url).endsWith('/users/me') &&
      (init?.method ?? 'GET') === 'GET'
    ) {
      return Promise.resolve(reply(200, restoredAccount))
    }
    throw new Error(`unexpected request: ${String(url)}`)
  })
  const user = userEvent.setup()
  renderAt('/account-recovery')

  await user.click(screen.getByRole('button', { name: 'Restore My Account' }))

  await waitFor(() =>
    expect(
      screen.queryByRole('heading', { name: 'Account Scheduled for Deletion' }),
    ).not.toBeInTheDocument(),
  )
  // the stored session no longer carries the flag, so nothing bounces
  // the user back here on the next reload
  const stored = JSON.parse(localStorage.getItem('user')!)
  expect(stored.accessToken).toBe('jwt')
  expect(stored.deletionScheduledFor).toBeUndefined()
  expect(sentRequests()).toContainEqual({
    url: 'http://user.test/users/me/recover',
    method: 'POST',
  })
})

test('Keep Deleted logs out: best-effort server logout, session cleared', async () => {
  localStorage.setItem('user', JSON.stringify(deletedSession))
  fetchMock.mockResolvedValue(reply(204, undefined))
  const user = userEvent.setup()
  renderAt('/account-recovery')

  await user.click(screen.getByRole('button', { name: 'Keep Deleted' }))

  await waitFor(() =>
    expect(JSON.parse(localStorage.getItem('user')!)).toBeNull(),
  )
  expect(sentRequests()).toContainEqual({
    url: 'http://user.test/auth/logout',
    method: 'POST',
  })
})

test('a reloaded flagged session is held on the recovery screen, not /profile', async () => {
  localStorage.setItem('user', JSON.stringify(deletedSession))

  renderAt('/profile')

  await waitFor(() =>
    expect(
      screen.getByRole('heading', { name: 'Account Scheduled for Deletion' }),
    ).toBeInTheDocument(),
  )
  // no data call went out for the page it was bounced from
  expect(sentRequests()).toEqual([])
})

test('an active session that wanders to /account-recovery is sent home', async () => {
  localStorage.setItem(
    'user',
    JSON.stringify({
      accessToken: 'jwt',
      tokenType: 'Bearer',
      expiresIn: 3600,
    }),
  )
  fetchMock.mockResolvedValue(reply(200, restoredAccount))

  renderAt('/account-recovery')

  await waitFor(() =>
    expect(
      screen.queryByRole('heading', { name: 'Account Scheduled for Deletion' }),
    ).not.toBeInTheDocument(),
  )
})
