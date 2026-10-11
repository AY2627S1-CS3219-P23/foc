// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
// Scope: tests for the two-step forgot-password page, in
// auth.test.tsx's harness (real apiFetch, fetch and the service base
// URL faked; the global router's navigate spied for cross-page hops).
// Pins: step 1 posts the email, shows only the neutral notice (an
// empty 202 — the page must never reveal whether the address has an
// account) and advances to the code step IDENTICALLY for known and
// unknown emails; step 2 posts email + code + double-entered password
// and hands off to /login with the reset notice; a reset-code-invalid
// refusal (matched by problem type, not wording) keeps the code step
// with its resend; the resend repeats the request; the login page
// shows the arrival notice. Rewritten from the link-token version the
// same day, when the owner revisited the credential to an OTP code.
// PR #179 Copilot review: a case pins that "Change email" clears the
// typed passwords instead of silently carrying them to the next
// address.
// Author review: Leong Wei Zhi to review via the PR.

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { RESET_CODE_INVALID } from '@/features/user/problemTypes'
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

function reply(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
) {
  return {
    ok: status < 400,
    status,
    headers: new Headers(headers),
    json: async () =>
      // the real 202 carries no body at all; json() rejects on it
      body === undefined ? Promise.reject(new SyntaxError('no body')) : body,
  }
}

function problem(status: number, detail: string, type?: string) {
  return reply(status, { status, detail, ...(type && { type }) })
}

// the method, URL and parsed body of the n-th request sent
function requestAt(n: number) {
  const [url, init] = fetchMock.mock.calls[n] as [string, RequestInit]
  return { url, method: init.method, body: JSON.parse(String(init.body)) }
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

async function requestCode(email: string) {
  const user = userEvent.setup()
  render(
    <RouterProvider
      router={createMemoryRouter(routes, {
        initialEntries: ['/forgot-password'],
      })}
    />,
  )
  await user.type(screen.getByLabelText('NUS Email'), email)
  await user.click(screen.getByRole('button', { name: 'Send Reset Code' }))
  return user
}

async function fillCodeStep(
  user: ReturnType<typeof userEvent.setup>,
  code: string,
  password = 'NewPassword456',
  confirm = 'NewPassword456',
) {
  await user.type(screen.getByLabelText('Reset Code'), code)
  await user.type(screen.getByLabelText('New Password'), password)
  await user.type(screen.getByLabelText('Confirm New Password'), confirm)
  await user.click(screen.getByRole('button', { name: 'Reset Password' }))
}

describe('request step', () => {
  test('posts the email, shows the one neutral notice, and reveals the code step', async () => {
    fetchMock.mockResolvedValueOnce(reply(202, undefined))

    await requestCode('e1234567@u.nus.edu')

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'If an account exists for that email, a reset code is on its way.',
      ),
    )
    expect(screen.getByLabelText('Reset Code')).toBeInTheDocument()
    expect(requestAt(0)).toEqual({
      url: 'http://user.test/auth/password-reset',
      method: 'POST',
      body: { email: 'e1234567@u.nus.edu' },
    })
  })

  test('an unknown email looks exactly the same — notice, code step and all', async () => {
    // the server's 202 is identical on purpose; the page must not add
    // any tell of its own
    fetchMock.mockResolvedValueOnce(reply(202, undefined))

    await requestCode('e9999999@u.nus.edu')

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'If an account exists for that email, a reset code is on its way.',
      ),
    )
    expect(screen.getByLabelText('Reset Code')).toBeInTheDocument()
  })

  test('shows the format 400 as sent and stays on the email step', async () => {
    fetchMock.mockResolvedValueOnce(
      problem(
        400,
        'Email must be a valid @u.nus.edu address. Email used should not be the friendly email.',
      ),
    )

    await requestCode('not-an-email@gmail.com')

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Email must be a valid @u.nus.edu address.',
      ),
    )
    expect(screen.queryByLabelText('Reset Code')).not.toBeInTheDocument()
  })
})

describe('code step', () => {
  test('posts email, code and double entry, then hands off to /login with the notice', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock
      .mockResolvedValueOnce(reply(202, undefined))
      .mockResolvedValueOnce(reply(204, undefined))

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '042042')

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login', {
        state: { passwordReset: true },
      }),
    )
    expect(requestAt(1)).toEqual({
      url: 'http://user.test/auth/password-reset/confirm',
      method: 'POST',
      body: {
        email: 'e1234567@u.nus.edu',
        code: '042042',
        newPassword: 'NewPassword456',
        confirmPassword: 'NewPassword456',
      },
    })
  })

  test('a mismatched double entry is refused locally, before any request', async () => {
    fetchMock.mockResolvedValueOnce(reply(202, undefined))

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '042042', 'NewPassword456', 'Different789')

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Password entries do not match.',
      ),
    )
    // only the request step's call went out
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  test('a reset-code-invalid refusal keeps the code step, with its resend', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(202, undefined))
      .mockResolvedValueOnce(
        problem(
          400,
          'Reset code is invalid or has expired; request a new one',
          RESET_CODE_INVALID,
        ),
      )

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '000000')

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'That code is invalid or has expired. Check it, or resend a new one.',
      ),
    )
    expect(screen.getByLabelText('Reset Code')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Resend code' }),
    ).toBeInTheDocument()
  })

  test('Change email clears the typed passwords (PR #179 review)', async () => {
    fetchMock.mockResolvedValue(reply(202, undefined))

    const user = await requestCode('e1234567@u.nus.edu')
    await user.type(screen.getByLabelText('New Password'), 'NewPassword456')
    await user.type(
      screen.getByLabelText('Confirm New Password'),
      'NewPassword456',
    )
    await user.click(screen.getByRole('button', { name: 'Change email' }))
    // back on step 1; request a code for a different address
    await user.clear(screen.getByLabelText('NUS Email'))
    await user.type(screen.getByLabelText('NUS Email'), 'e7654321@u.nus.edu')
    await user.click(screen.getByRole('button', { name: 'Send Reset Code' }))

    // the passwords typed for the first address must not carry over
    await waitFor(() =>
      expect(screen.getByLabelText('New Password')).toHaveValue(''),
    )
    expect(screen.getByLabelText('Confirm New Password')).toHaveValue('')
  })

  test('Resend code repeats the request for the same address', async () => {
    fetchMock.mockResolvedValue(reply(202, undefined))

    const user = await requestCode('e1234567@u.nus.edu')
    await user.click(screen.getByRole('button', { name: 'Resend code' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(requestAt(1)).toEqual({
      url: 'http://user.test/auth/password-reset',
      method: 'POST',
      body: { email: 'e1234567@u.nus.edu' },
    })
    // still on the code step
    expect(screen.getByLabelText('Reset Code')).toBeInTheDocument()
  })
})

describe('login page arrival notice', () => {
  test('shows the password-reset notice the code step hands over', () => {
    render(
      <RouterProvider
        router={createMemoryRouter(routes, {
          initialEntries: [
            { pathname: '/login', state: { passwordReset: true } },
          ],
        })}
      />,
    )

    expect(screen.getByRole('status')).toHaveTextContent(
      'Password reset. Log in with your new password.',
    )
  })
})
