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
// 2026-10-16, Claude Code (Opus 5), PR #180 main sync: two cases for a
// stale stored session, which #182 made consequential — neither reset
// call may carry the token, and a landed reset drops the stored session.
// initFor() finds a call by path because a seeded session adds a
// GET /users/me on mount that would shift the positional indices.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-17, Codex (GPT-6), PR #180 review, issue #94:
// AI-generated (edited by Codex; Leong Wei Zhi review pending).
// Exercise reset through the shared CodeInput and expose 202 response text.
// Author review: Leong Wei Zhi to review via PR #180.

import { render, screen, waitFor, within } from '@testing-library/react'
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
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () =>
      // the real 202 carries no body at all; json() rejects on it
      body === undefined ? Promise.reject(new SyntaxError('no body')) : body,
  }
}

function problem(status: number, detail: string, type?: string) {
  return reply(status, { status, detail, ...(type && { type }) })
}

// the init of the (single) request sent to a path, found by URL rather
// than position: a seeded session makes AuthProvider fetch GET /users/me
// on mount, which would shift requestAt()'s indices
function initFor(path: string) {
  const call = fetchMock.mock.calls.find(([url]) =>
    String(url).endsWith(path),
  ) as [string, RequestInit] | undefined
  if (!call) throw new Error(`no request to ${path}`)
  return call[1]
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
  await user.click(
    within(screen.getByRole('group', { name: 'Reset Code' })).getAllByRole(
      'textbox',
    )[0],
  )
  await user.paste(code)
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
    expect(
      screen.getByRole('group', { name: 'Reset Code' }),
    ).toBeInTheDocument()
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
    expect(
      screen.getByRole('group', { name: 'Reset Code' }),
    ).toBeInTheDocument()
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
    expect(
      screen.queryByRole('group', { name: 'Reset Code' }),
    ).not.toBeInTheDocument()
  })
})

describe('code step', () => {
  test('uses six digit boxes, filters pasted text, and refuses an incomplete code', async () => {
    fetchMock.mockResolvedValueOnce(reply(202, undefined))
    const user = await requestCode('e1234567@u.nus.edu')
    const boxes = within(
      screen.getByRole('group', { name: 'Reset Code' }),
    ).getAllByRole('textbox')
    expect(boxes).toHaveLength(6)
    const submit = screen.getByRole('button', { name: 'Reset Password' })
    expect(submit).toBeDisabled()
    await user.click(boxes[0])
    await user.paste('a01b23c4567')
    expect(boxes.map((box) => (box as HTMLInputElement).value).join('')).toBe(
      '012345',
    )
    expect(submit).toBeEnabled()
    await user.clear(boxes[2])
    expect(submit).toBeDisabled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

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
    expect(
      screen.getByRole('group', { name: 'Reset Code' }),
    ).toBeInTheDocument()
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
    expect(
      screen.getByRole('group', { name: 'Reset Code' }),
    ).toBeInTheDocument()
  })
})

describe('a signed-in visitor', () => {
  // this page is public and reachable with a live session. #182 made a
  // token-bearing 401 force a sign-out, and these are credential
  // endpoints, so the token must stay off them (PR #180 main sync):
  // otherwise a refusal of the *reset* reads as a revoked session and
  // signs the user out mid-flow with nothing shown, because a public
  // page raises no notice and no redirect.
  const account = {
    id: 1,
    username: 'tester',
    email: 'e1234567@u.nus.edu',
    role: 'USER',
  }

  // a live session: GET /users/me answers, so the token source stays
  // loaded and would be injected into anything that does not opt out
  function signedIn(confirmReply: ReturnType<typeof reply>) {
    localStorage.setItem('user', JSON.stringify({ accessToken: 'live-jwt' }))
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        String(url).endsWith('/users/me')
          ? reply(200, account)
          : String(url).endsWith('/confirm')
            ? confirmReply
            : reply(202, undefined),
      ),
    )
  }

  test('sends neither reset call with the bearer token', async () => {
    signedIn(reply(204, undefined))
    vi.spyOn(router, 'navigate').mockResolvedValue()

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '042042')

    await waitFor(() => expect(initFor('/password-reset/confirm')).toBeTruthy())
    // the session really was live: the mount fetch carried the token
    expect(new Headers(initFor('/users/me').headers).get('Authorization')).toBe(
      'Bearer live-jwt',
    )
    for (const path of ['/auth/password-reset', '/password-reset/confirm']) {
      expect(new Headers(initFor(path).headers).has('Authorization')).toBe(
        false,
      )
    }
  })

  // the reset evicted every session the account held (G11), so the
  // stored token is dead by the time this page leaves
  test('is signed out once the reset lands', async () => {
    signedIn(reply(204, undefined))
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '042042')

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login', {
        state: { passwordReset: true },
      }),
    )
    expect(localStorage.getItem('user')).toBe('null')
  })

  // only a landed reset ends it — a refused code leaves the user both
  // on the code step and signed in
  test('keeps the session when the reset is refused', async () => {
    signedIn(problem(400, 'That code is invalid.', RESET_CODE_INVALID))

    const user = await requestCode('e1234567@u.nus.edu')
    await fillCodeStep(user, '000000')

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(localStorage.getItem('user')).toBe(
      JSON.stringify({ accessToken: 'live-jwt' }),
    )
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
    // and it is the only notice: the reset ended the session itself, so
    // #178's "session ended" box has no flag to raise here
    expect(screen.getAllByRole('status')).toHaveLength(1)
  })
})
