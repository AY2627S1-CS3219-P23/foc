// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: tests for the login and sign-up pages against a stubbed fetch:
// the request each page sends, a successful login storing the session
// and leaving the page, sign-up sending the user to log in, the server's
// problem+json reason shown on failure, and the fallback text for a
// network failure; the "account created" notice after sign-up.
// Author review: Ryan to review via the PR.

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { router, routes } from '../routes'

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
  return { ok: status < 400, status, json: async () => body }
}

function problem(status: number, detail: string) {
  return reply(status, { status, detail })
}

// the method, URL and parsed body of the n-th request sent
function requestAt(n: number) {
  const [url, init] = fetchMock.mock.calls[n] as [string, RequestInit]
  return { url, method: init.method, body: JSON.parse(String(init.body)) }
}

// the one request sent
function sentRequest() {
  expect(fetchMock).toHaveBeenCalledTimes(1)
  return requestAt(0)
}

// the page's own submit button; the nav bar has a "Log In" button too
function submitButton(name: string) {
  const form = screen.getByLabelText('Password').closest('form')
  if (!form) throw new Error('no form on the page')
  return within(form).getByRole('button', { name })
}

function renderAt(path: string, state?: unknown) {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, {
        initialEntries: [{ pathname: path, state }],
      })}
    />,
  )
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

const session = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 }

async function logIn(identifier: string, password: string) {
  const user = userEvent.setup()
  renderAt('/login')
  await user.type(screen.getByLabelText('Username or Email'), identifier)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(submitButton('Log In'))
}

describe('login page', () => {
  test('sends usernameOrEmail and password, stores the session and leaves the page', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(200, session))
      // GET /users/me, asked once the session is stored
      .mockResolvedValue(
        reply(200, {
          id: 1,
          email: 'e1234567@u.nus.edu',
          username: 'student_alex',
          role: 'USER',
          createdAt: '2026-09-01T00:00:00Z',
        }),
      )

    await logIn('student_alex', 'Password1234')

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Log In' }),
      ).not.toBeInTheDocument(),
    )
    expect(requestAt(0)).toEqual({
      url: 'http://user.test/auth/login',
      method: 'POST',
      body: { usernameOrEmail: 'student_alex', password: 'Password1234' },
    })
    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(session)
    // then the account, with the new token
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [meUrl, meInit] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(meUrl).toBe('http://user.test/users/me')
    expect(new Headers(meInit.headers).get('Authorization')).toBe('Bearer jwt')
  })

  test("shows the server's reason for a failed login", async () => {
    fetchMock.mockResolvedValue(
      problem(401, 'Incorrect username/email or password'),
    )

    await logIn('student_alex', 'WrongPassword1')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Incorrect username/email or password',
    )
    expect(localStorage.getItem('user')).toBe('null')
  })

  test('shows the lockout message on a 429', async () => {
    fetchMock.mockResolvedValue(
      problem(429, 'Too many failed attempts. Login disabled for 15 minutes.'),
    )

    await logIn('student_alex', 'WrongPassword1')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many failed attempts. Login disabled for 15 minutes.',
    )
  })

  test('says the account was created when arriving from sign-up', () => {
    renderAt('/login', { accountCreated: true })

    expect(screen.getByRole('status')).toHaveTextContent(
      'Account created. Log in to continue.',
    )
  })

  test('shows no notice when opened directly', () => {
    renderAt('/login')

    expect(
      screen.queryByText('Account created. Log in to continue.'),
    ).not.toBeInTheDocument()
  })

  test('shows a general message when the service is unreachable', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await logIn('student_alex', 'Password1234')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not log in. Try again.',
    )
  })
})

describe('sign-up page', () => {
  async function signUp() {
    const user = userEvent.setup()
    renderAt('/register')
    await user.type(
      screen.getByLabelText('NUS Email Address'),
      'e1234567@u.nus.edu',
    )
    await user.type(screen.getByLabelText('Username'), 'student_alex')
    await user.type(screen.getByLabelText('Password'), 'Password1234')
    await user.click(submitButton('Sign Up'))
  }

  test('sends the account details and goes to the login page', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock.mockResolvedValue(
      reply(201, {
        id: 1,
        email: 'e1234567@u.nus.edu',
        username: 'student_alex',
      }),
    )

    await signUp()

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login', {
        state: { accountCreated: true },
      }),
    )
    expect(sentRequest()).toEqual({
      url: 'http://user.test/auth/signup',
      method: 'POST',
      body: {
        email: 'e1234567@u.nus.edu',
        username: 'student_alex',
        password: 'Password1234',
      },
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test("shows the server's reason when sign-up is rejected", async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock.mockResolvedValue(problem(400, 'Username is already taken'))

    await signUp()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Username is already taken',
    )
    expect(navigate).not.toHaveBeenCalled()
  })

  test('the password checklist follows what is typed', async () => {
    const user = userEvent.setup()
    renderAt('/register')

    const lengthRule = screen.getByText(/Length between 10 and 50 characters/)
    expect(lengthRule).toHaveTextContent('✗')

    await user.type(screen.getByLabelText('Password'), 'Password1234')

    expect(lengthRule).toHaveTextContent('✓')
    expect(screen.getByText(/Contains numbers/)).toHaveTextContent('✓')
  })
})
