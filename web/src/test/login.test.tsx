// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-09-29, issue #146.
// Scope: tests for the login page's error alert. user-service now sends a
// different problem+json detail per failure (unknown account, wrong
// password with the attempts left, locked with the time left), and the
// page is supposed to show each one as sent — these pin that, through the
// real apiFetch/ApiError, with only fetch and the service base URL faked.
// Reviewed by: Leong Wei Zhi (via pull request).

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { routes } from '../routes'

// The base URLs are inlined from VITE_* at build time and no .env is read
// in tests, so the user service would otherwise have no URL at all.
vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://user.test',
    supplier: '',
    order: '',
    credit: '',
    notification: '',
  },
}))

// What user-service answers POST /auth/login with, verbatim.
function problemResponse(status: number, detail: string) {
  return new Response(JSON.stringify({ type: 'about:blank', status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function respondWith(response: Response | Error) {
  const fetchMock = vi.fn(() =>
    response instanceof Error
      ? Promise.reject(response)
      : Promise.resolve(response),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

async function submitLogin(identifier = 'student_alex', password = 'wrong') {
  const user = userEvent.setup()
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/login'] })}
    />,
  )

  const identifierField = screen.getByLabelText(/Username or Email/)
  await user.type(identifierField, identifier)
  await user.type(screen.getByLabelText(/^Password/), password)

  // the shell's nav carries its own "Log In", so submit from the form
  const form = identifierField.closest('form')
  if (!form) throw new Error('The login fields are not inside a form')
  await user.click(within(form).getByRole('button', { name: 'Log In' }))
}

async function expectAlert(text: string) {
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(text))
}

test('shows the unknown-account 401 detail as the server sent it', async () => {
  respondWith(
    problemResponse(401, 'No account found for that username or email.'),
  )

  await submitLogin('nobody')

  await expectAlert('No account found for that username or email.')
})

test('shows the wrong-password 401 detail, attempts countdown included', async () => {
  respondWith(
    problemResponse(
      401,
      'Incorrect password. 2 attempts remaining before your account is temporarily locked.',
    ),
  )

  await submitLogin()

  await expectAlert(
    'Incorrect password. 2 attempts remaining before your account is temporarily locked.',
  )
})

test('shows the lockout 429 detail with the time left', async () => {
  respondWith(
    problemResponse(
      429,
      'Your account is locked due to too many failed login attempts. Try again in 12 minutes.',
    ),
  )

  await submitLogin()

  await expectAlert(
    'Your account is locked due to too many failed login attempts. Try again in 12 minutes.',
  )
})

test('falls back to its own message when the network fails', async () => {
  respondWith(new TypeError('Failed to fetch'))

  await submitLogin()

  await expectAlert('Could not log in. Try again.')
})
