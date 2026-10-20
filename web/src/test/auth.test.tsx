// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: tests for the login and sign-up pages against a stubbed fetch:
// the request each page sends, a successful login storing the session
// and leaving the page, sign-up sending the user to log in, the server's
// problem+json reason shown on failure, and the fallback text for a
// network failure; the "account created" notice after sign-up.
// 2026-09-30, Claude Code (Opus 5), issue #109 (PR #150): sign-up now ends
// in the OTP dialog, so its cases cover the 202 opening the dialog and NOT
// navigating (the dead-end the review found), the verify call that creates
// the account, a wrong code, the failures that send the user back to the
// form, the resend and its cooldown, and the six-box code field. reply()
// gained headers so ApiError can read Retry-After.
// 2026-09-30, Claude Code (Opus 5), PR #150 review (@Sinnez1): cases for
// the four dialog fixes — correcting a middle digit, reopening a dismissed
// dialog, a 500 keeping the dialog open, and Verify being disabled while a
// resend is in flight.
// 2026-09-30, Claude Code (Opus 5), PR #150 approval notes: the expiry
// line keeps up with the clock and says plainly when the code has run out.
// 2026-10-05, Claude Code (Opus 5.5), issue #154: the "account created"
// notice clears its navigation state, so a reload doesn't show it again.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: the
// back-to-the-form cases now include the untyped refusal as the server
// really sends it — `type: "about:blank"`, which the dialog had been
// reading as a type of its own.
// 2026-10-10, Claude Code (Opus 5.5), PR #162 re-review (Leong Wei Zhi):
// clearing that state keeps the URL's query and hash.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): and an
// untyped 429, which ends the sign-up on its status alone.
// 2026-10-11, Claude Code (Fable 5), issue #151: logout cases — the nav
// Logout button POSTs the bearer token to /auth/logout, and the local
// sign-out survives the call failing (best-effort, team decision).
// 2026-10-12, Claude Code (Fable 5), issue #178: revoked-session cases —
// a 401 on a token-bearing request signs the user out to /login with the
// "session ended" notice, and logout's own best-effort 401 does not.
// PR #182 Copilot review: a wrong-credentials login with a stale stored
// session keeps its inline error — the login call carries no token.
// 2026-10-13, Claude Code (Opus 5), PR #182 review (@Sinnez1): a 401
// answering a token the user has already replaced leaves the new session
// alone, and a 401 landing while the login page is open shows the notice
// there instead of saving it for a later visit.
// Author review: Ryan to review via the PR.
// 2026-10-17, Codex (GPT-6), PR #180 review, issue #94:
// AI-generated (edited by Codex; Leong Wei Zhi review pending).
// Provide response text in the sign-up 202 mocks for the stricter parser.
// Author review: Leong Wei Zhi to review via PR #180.

import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import {
  OTP_ATTEMPTS_EXCEEDED,
  OTP_EXPIRED,
} from '@/features/user/problemTypes'
import { clearSessionEnded } from '@/features/user/sessionNotice'
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
    json: async () => body,
  }
}

function problem(
  status: number,
  detail: string,
  headers?: Record<string, string>,
  // the stable problem+json type URI user-service attaches to its OTP and
  // uniqueness refusals (PR #157); the dialog matches on it, not on detail
  type?: string,
) {
  return reply(status, { status, detail, ...(type && { type }) }, headers)
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
  // the "session ended" flag is module state, so it outlives a test
  clearSessionEnded()
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

  test('clears the sign-up state once read, so a reload shows no notice', async () => {
    const memoryRouter = createMemoryRouter(routes, {
      initialEntries: [{ pathname: '/login', state: { accountCreated: true } }],
    })
    render(<RouterProvider router={memoryRouter} />)

    await waitFor(() => expect(memoryRouter.state.location.state).toBeNull())
    expect(memoryRouter.state.location.pathname).toBe('/login')
    // still shown on this visit
    expect(screen.getByRole('status')).toHaveTextContent(
      'Account created. Log in to continue.',
    )
  })

  test('clearing the sign-up state keeps the query and hash', async () => {
    const memoryRouter = createMemoryRouter(routes, {
      initialEntries: [
        {
          pathname: '/login',
          search: '?redirect=%2Forders',
          hash: '#form',
          state: { accountCreated: true },
        },
      ],
    })
    render(<RouterProvider router={memoryRouter} />)

    await waitFor(() => expect(memoryRouter.state.location.state).toBeNull())
    expect(memoryRouter.state.location.search).toBe('?redirect=%2Forders')
    expect(memoryRouter.state.location.hash).toBe('#form')
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
  // what POST /auth/signup answers with: a code is on its way, no account
  const accepted = {
    email: 'e1234567@u.nus.edu',
    expiresInSeconds: 600,
    resendInSeconds: 60,
  }
  const account = {
    id: 1,
    email: 'e1234567@u.nus.edu',
    username: 'student_alex',
  }

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
    return user
  }

  const otpDialog = () =>
    within(screen.getByRole('dialog', { name: 'OTP Verification' }))

  // the boxes advance focus themselves, so one keyboard burst fills them
  async function typeCode(
    user: ReturnType<typeof userEvent.setup>,
    code: string,
  ) {
    await user.click(otpDialog().getByLabelText('Digit 1 of 6'))
    await user.keyboard(code)
  }

  const signupRequest = {
    url: 'http://user.test/auth/signup',
    method: 'POST',
    body: {
      email: 'e1234567@u.nus.edu',
      username: 'student_alex',
      password: 'Password1234',
    },
  }

  test('the 202 opens the code dialog instead of going to the login page', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock.mockResolvedValue(reply(202, accepted))

    await signUp()

    // the bug this replaces: sign-up used to treat any 2xx as done and
    // leave, so the account was never created and login always failed
    expect(
      await screen.findByRole('dialog', { name: 'OTP Verification' }),
    ).toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
    expect(sentRequest()).toEqual(signupRequest)
  })

  test('the emailed code creates the account and goes to the login page', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock
      .mockResolvedValueOnce(reply(202, accepted))
      .mockResolvedValueOnce(reply(201, account))

    const user = await signUp()
    await typeCode(user, '482910')
    await user.click(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    )

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login', {
        state: { accountCreated: true },
      }),
    )
    expect(requestAt(1)).toEqual({
      url: 'http://user.test/auth/signup/verify',
      method: 'POST',
      body: { email: 'e1234567@u.nus.edu', code: '482910' },
    })
  })

  test('a wrong code keeps the dialog open with the server reason', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock
      .mockResolvedValueOnce(reply(202, accepted))
      .mockResolvedValueOnce(problem(400, 'Invalid verification code'))

    const user = await signUp()
    await typeCode(user, '111111')
    await user.click(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    )

    expect(await otpDialog().findByRole('alert')).toHaveTextContent(
      'Invalid verification code',
    )
    expect(
      screen.getByRole('dialog', { name: 'OTP Verification' }),
    ).toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
  })

  test.each([
    [
      'an expired code',
      400,
      'Code has expired; sign up again to get a new code',
      OTP_EXPIRED,
    ],
    [
      'too many wrong codes',
      429,
      'Too many incorrect codes; sign up again to get a new code',
      OTP_ATTEMPTS_EXCEEDED,
    ],
    [
      // no type at all: only an exhausted attempt budget answers verify
      // with a 429, and that row refuses every code, so the status is
      // enough to end the dialog (PR #149 review, @Sinnez1)
      'an untyped 429',
      429,
      'Too many incorrect codes; sign up again to get a new code',
      undefined,
    ],
    [
      // AuthService's post-flush race is a plain ResponseStatusException,
      // so Spring fills `type` with the placeholder `about:blank` — no
      // type at all, and the sentence is the only signal. Only the form
      // has the field that needs changing.
      'an identifier taken after the checks',
      400,
      'Email or username was just taken; choose another',
      'about:blank',
    ],
  ])(
    '%s sends the user back to the filled form',
    async (_case, status, detail, type) => {
      fetchMock
        .mockResolvedValueOnce(reply(202, accepted))
        .mockResolvedValueOnce(problem(status, detail, {}, type))

      const user = await signUp()
      await typeCode(user, '482910')
      await user.click(
        otpDialog().getByRole('button', { name: 'Verify & Activate' }),
      )

      // the pending sign-up is gone server-side, so the dialog would be
      // lying if it stayed; the typed details are kept for one more try
      await waitFor(() =>
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
      )
      expect(screen.getByRole('alert')).toHaveTextContent(detail)
      expect(screen.getByLabelText('NUS Email Address')).toHaveValue(
        'e1234567@u.nus.edu',
      )
    },
  )

  test('resend re-posts the identical sign-up request', async () => {
    fetchMock
      // cooldown off, so the button is live at once
      .mockResolvedValueOnce(reply(202, { ...accepted, resendInSeconds: 0 }))
      .mockResolvedValueOnce(reply(202, { ...accepted, resendInSeconds: 0 }))

    const user = await signUp()
    await user.click(otpDialog().getByRole('button', { name: 'Resend code' }))

    // byte-identical, because user-service accepts only a repeat of the
    // same request as a resend — anything else is a 409
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(requestAt(1)).toEqual(requestAt(0))
    expect(await otpDialog().findByRole('status')).toHaveTextContent(
      'A new code has been sent.',
    )
  })

  test('the resend button waits out the cooldown from the 202', async () => {
    fetchMock.mockResolvedValue(reply(202, accepted))

    await signUp()

    const resend = await otpDialog().findByRole('button', {
      name: 'Resend code in 60s',
    })
    expect(resend).toBeDisabled()
  })

  test('a refused resend quotes the wait from Retry-After', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(202, { ...accepted, resendInSeconds: 0 }))
      .mockResolvedValueOnce(
        problem(
          429,
          'A verification code was sent to this email moments ago. Try again in 43 seconds.',
          { 'Retry-After': '43' },
        ),
      )

    const user = await signUp()
    await user.click(otpDialog().getByRole('button', { name: 'Resend code' }))

    expect(await otpDialog().findByRole('alert')).toHaveTextContent(
      'Try again in 43 seconds.',
    )
    expect(
      otpDialog().getByRole('button', { name: /Resend code in 4[0-3]s/ }),
    ).toBeDisabled()
  })

  test('closing the dialog leaves the form filled and sends nothing', async () => {
    fetchMock.mockResolvedValue(reply(202, accepted))

    const user = await signUp()
    await user.click(otpDialog().getByRole('button', { name: 'Close' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveValue('student_alex')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  test('pasting the code fills every box', async () => {
    fetchMock.mockResolvedValue(reply(202, accepted))

    const user = await signUp()
    await user.click(otpDialog().getByLabelText('Digit 1 of 6'))
    await user.paste('482193')

    expect(otpDialog().getByLabelText('Digit 1 of 6')).toHaveValue('4')
    expect(otpDialog().getByLabelText('Digit 6 of 6')).toHaveValue('3')
  })

  test('a middle digit can be corrected without shifting the rest', async () => {
    fetchMock.mockResolvedValue(reply(202, accepted))

    const user = await signUp()
    await typeCode(user, '123456')
    // clear the third box and type the right digit in place
    await user.click(otpDialog().getByLabelText('Digit 3 of 6'))
    await user.keyboard('{Backspace}9')

    expect(otpDialog().getByLabelText('Digit 3 of 6')).toHaveValue('9')
    expect(otpDialog().getByLabelText('Digit 4 of 6')).toHaveValue('4')
    expect(otpDialog().getByLabelText('Digit 6 of 6')).toHaveValue('6')
    // six digits, so Verify is live rather than stuck at five
    expect(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    ).toBeEnabled()
  })

  test('closing the dialog keeps the code enterable', async () => {
    fetchMock.mockResolvedValue(reply(202, accepted))

    const user = await signUp()
    await user.click(otpDialog().getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    // the emailed code is still live, so it must not take a resend to
    // get back to entering it
    await user.click(screen.getByRole('button', { name: 'Enter your code' }))

    expect(
      screen.getByRole('dialog', { name: 'OTP Verification' }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  test('a server error keeps the dialog open, code still valid', async () => {
    fetchMock
      .mockResolvedValueOnce(reply(202, accepted))
      .mockResolvedValueOnce(problem(500, 'Internal Server Error'))

    const user = await signUp()
    await typeCode(user, '482910')
    await user.click(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    )

    expect(await otpDialog().findByRole('alert')).toBeInTheDocument()
    expect(
      screen.getByRole('dialog', { name: 'OTP Verification' }),
    ).toBeInTheDocument()
  })

  test('Verify is blocked while a resend is in flight', async () => {
    let releaseResend: (value: unknown) => void = () => {}
    fetchMock
      .mockResolvedValueOnce(reply(202, { ...accepted, resendInSeconds: 0 }))
      .mockReturnValueOnce(
        new Promise((resolve) => {
          releaseResend = resolve
        }),
      )

    const user = await signUp()
    await typeCode(user, '482910')
    await user.click(otpDialog().getByRole('button', { name: 'Resend code' }))

    // the resend is replacing the code, so verifying the old one would
    // spend an attempt for nothing
    expect(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    ).toBeDisabled()
    releaseResend(reply(202, { ...accepted, resendInSeconds: 0 }))
  })

  test('a code whose life has run out says so', async () => {
    fetchMock.mockResolvedValue(
      reply(202, { ...accepted, expiresInSeconds: 0 }),
    )

    await signUp()

    // rather than "The code expires in 0 seconds."
    expect(
      await otpDialog().findByText('This code has expired — send a new one.'),
    ).toBeInTheDocument()
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

  test('a sign-up already in progress is shown on the form, with no dialog', async () => {
    fetchMock.mockResolvedValue(
      problem(
        409,
        'A sign-up for this email is already in progress; check your inbox for the code, or try again once it expires',
      ),
    )

    await signUp()

    // this browser may not own that pending sign-up, so opening the code
    // dialog would be a guess
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'already in progress',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
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

describe('sign-up-owner page', () => {
  // what POST /auth/signup answers with: a code is on its way, no account
  const accepted = {
    email: 'e1234567@u.nus.edu',
    expiresInSeconds: 600,
    resendInSeconds: 60,
  }
  const account = {
    id: 1,
    email: 'e1234567@u.nus.edu',
    username: 'student_alex',
  }

  async function signUp() {
    const user = userEvent.setup()
    renderAt('/register-owner')
    await user.type(
      screen.getByLabelText('NUS Email Address'),
      'e1234567@u.nus.edu',
    )
    await user.type(screen.getByLabelText('Username'), 'student_alex')
    await user.type(screen.getByLabelText('Password'), 'Password1234')
    await user.type(screen.getByLabelText('Setup token'), 'Token1234')
    await user.click(submitButton('Sign Up'))
    return user
  }

  const otpDialog = () =>
    within(screen.getByRole('dialog', { name: 'OTP Verification' }))

  // the boxes advance focus themselves, so one keyboard burst fills them
  async function typeCode(
    user: ReturnType<typeof userEvent.setup>,
    code: string,
  ) {
    await user.click(otpDialog().getByLabelText('Digit 1 of 6'))
    await user.keyboard(code)
  }

  const signupRequest = {
    url: 'http://user.test/auth/signup-owner',
    method: 'POST',
    body: {
      email: 'e1234567@u.nus.edu',
      username: 'student_alex',
      password: 'Password1234',
      ownerKey: 'Token1234'
    },
  }

  test('signup from registerOwner sends different json body to different endpoint', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock.mockResolvedValue(reply(202, accepted))

    await signUp()

    expect(
      await screen.findByRole('dialog', { name: 'OTP Verification' }),
    ).toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
    expect(sentRequest()).toEqual(signupRequest)
  })

  test('verify otp from registerOwner sends to different endpoint', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue()
    fetchMock
      .mockResolvedValueOnce(reply(202, accepted))
      .mockResolvedValueOnce(reply(201, account))

    const user = await signUp()
    await typeCode(user, '482910')
    await user.click(
      otpDialog().getByRole('button', { name: 'Verify & Activate' }),
    )

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login', {
        state: { accountCreated: true },
      }),
    )
    expect(requestAt(1)).toEqual({
      url: 'http://user.test/auth/signup/verify-owner',
      method: 'POST',
      body: { email: 'e1234567@u.nus.edu', code: '482910' },
    })
  })
})

// issue #151: logging out also revokes the token server-side
describe('logout', () => {
  async function logInAndWaitForNav() {
    fetchMock
      .mockResolvedValueOnce(reply(200, session))
      // GET /users/me, asked once the session is stored
      .mockResolvedValue(
        reply(200, {
          id: 1,
          username: 'alex',
          email: 'e@u.nus.edu',
          role: 'USER',
        }),
      )
    await logIn('alex', 'ValidPassword123')
    return await screen.findByRole('button', { name: 'Logout' })
  }

  test('posts the token to /auth/logout and clears the session', async () => {
    const user = userEvent.setup()
    const logoutButton = await logInAndWaitForNav()
    fetchMock.mockClear()
    fetchMock.mockResolvedValue(reply(204, null))

    await user.click(logoutButton)

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).endsWith('/auth/logout'),
      ) as [string, RequestInit] | undefined
      expect(call).toBeDefined()
      expect(call?.[1].method).toBe('POST')
      expect(new Headers(call?.[1].headers).get('Authorization')).toBe(
        'Bearer jwt',
      )
    })
    expect(localStorage.getItem('user')).toBe('null')
    // signed out: the nav bar's Logout button is gone
    expect(
      screen.queryByRole('button', { name: 'Logout' }),
    ).not.toBeInTheDocument()
  })

  test('still signs out locally when the server call fails', async () => {
    const user = userEvent.setup()
    const logoutButton = await logInAndWaitForNav()
    fetchMock.mockClear()
    // best-effort by team decision: the revocation is lost, the sign-out isn't
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await user.click(logoutButton)

    expect(localStorage.getItem('user')).toBe('null')
    expect(
      screen.queryByRole('button', { name: 'Logout' }),
    ).not.toBeInTheDocument()
  })
})

// issue #178: a 401 on a request that carried the bearer token means the
// session was revoked server-side (admin demotion/removal, a password
// reset elsewhere, or plain expiry) — the app signs out instead of
// leaving the user on a page whose every call fails
describe('revoked session', () => {
  const SESSION_ENDED = 'Your session has ended. Please sign in again.'
  // the GET /users/me a live session answers with
  const me = reply(200, {
    id: 1,
    username: 'alex',
    email: 'e@u.nus.edu',
    role: 'USER',
  })

  test('a 401 on a token-bearing request signs out to the login page with a notice', async () => {
    localStorage.setItem('user', JSON.stringify(session))
    // GET /users/me, asked on mount with the stored token
    fetchMock.mockResolvedValue(problem(401, 'Token has been revoked'))

    renderAt('/profile')

    expect(await screen.findByRole('status')).toHaveTextContent(SESSION_ENDED)
    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument()
    expect(localStorage.getItem('user')).toBe('null')
  })

  test("logout's own best-effort 401 does not bounce the signed-out user to the login page", async () => {
    const user = userEvent.setup()
    // reuse the logged-in shell from the logout cases
    fetchMock.mockResolvedValueOnce(reply(200, session)).mockResolvedValue(
      reply(200, {
        id: 1,
        username: 'alex',
        email: 'e@u.nus.edu',
        role: 'USER',
      }),
    )
    await logIn('alex', 'ValidPassword123')
    const logoutButton = await screen.findByRole('button', { name: 'Logout' })
    fetchMock.mockClear()
    // the token was already revoked server-side; the sign-out had begun
    // locally, so the late 401 must not re-navigate (guard in AuthProvider)
    fetchMock.mockResolvedValue(problem(401, 'Token has been revoked'))

    await user.click(logoutButton)

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(localStorage.getItem('user')).toBe('null')
    // landed on the home page per the manual logout, not on /login
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Log In' }),
    ).not.toBeInTheDocument()
  })

  // PR #182 Copilot review: with a stale session stored, the login form's
  // own 401 must stay the form's inline error — the login call opts out
  // of the token injection, so the handler never sees it
  test('a wrong-credentials login with a stale stored session keeps its inline error', async () => {
    localStorage.setItem('user', JSON.stringify(session))
    const user = userEvent.setup()
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        String(url).endsWith('/auth/login')
          ? problem(401, 'Incorrect username/email or password')
          : // GET /users/me from the stored session's mount fetch
            reply(200, {
              id: 1,
              username: 'alex',
              email: 'e@u.nus.edu',
              role: 'USER',
            }),
      ),
    )

    renderAt('/login')
    await user.type(screen.getByLabelText('Username or Email'), 'alex')
    await user.type(screen.getByLabelText('Password'), 'WrongPassword1')
    await user.click(submitButton('Log In'))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Incorrect username/email or password',
    )
    // the login request carried no bearer token (auth: false)
    const loginCall = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith('/auth/login'),
    ) as [string, RequestInit]
    expect(new Headers(loginCall[1].headers).get('Authorization')).toBeNull()
    // not treated as a revoked session: no forced sign-out, no notice
    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(session)
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
  })

  // PR #182 review (@Sinnez1): the sign-out used to need only that some
  // session was live, so a 401 crawling in for a token the user had
  // already replaced ended the session they had just started
  test('a 401 answering a replaced token leaves the new session alone', async () => {
    const user = userEvent.setup()
    // a stale session is stored, so the mount asks GET /users/me with it
    localStorage.setItem('user', JSON.stringify(session))
    const newSession = { ...session, accessToken: 'jwt2' }
    let answerStaleMe: (value: unknown) => void = () => {}
    fetchMock.mockImplementation((url: string, init: RequestInit) => {
      if (String(url).endsWith('/auth/login')) {
        return Promise.resolve(reply(200, newSession))
      }
      // the stale token's account check hangs; the new one's answers
      return new Headers(init.headers).get('Authorization') === 'Bearer jwt'
        ? new Promise((resolve) => {
            answerStaleMe = resolve
          })
        : Promise.resolve(me)
    })

    renderAt('/login')
    await user.type(screen.getByLabelText('Username or Email'), 'alex')
    await user.type(screen.getByLabelText('Password'), 'ValidPassword123')
    await user.click(submitButton('Log In'))

    // signed in again, on a token of its own
    await screen.findByRole('button', { name: 'Logout' })
    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(
      newSession,
    )

    // only now does the old token's request come back refused
    await act(async () => {
      answerStaleMe(problem(401, 'Token has been revoked'))
    })

    // it says nothing about the session live now, which stands
    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(
      newSession,
    )
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Log In' }),
    ).not.toBeInTheDocument()
  })

  // PR #182 review (@Sinnez1): the notice was read at mount only, so a
  // 401 arriving with the page already open was skipped — and the unspent
  // flag then surfaced on the next visit, after a plain manual logout
  test('a 401 while the login page is open shows the notice there, not on a later visit', async () => {
    // the stored token is dead, and the user is sitting on /login
    localStorage.setItem('user', JSON.stringify(session))
    let answerMe: (value: unknown) => void = () => {}
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          answerMe = resolve
        }),
    )

    renderAt('/login')
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()

    // the mount's GET /users/me is refused while the page is still up
    await act(async () => {
      answerMe(problem(401, 'Token has been revoked'))
    })

    expect(screen.getByRole('status')).toHaveTextContent(SESSION_ENDED)
    expect(localStorage.getItem('user')).toBe('null')

    // the notice was spent on that showing: coming back to the page
    // (after a manual logout, say) is clean
    cleanup()
    renderAt('/login')
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
  })
  // PR #182 re-review (@Sinnez1): the session lives under one
  // localStorage key every tab shares, so a 401 for this tab's token must
  // leave a newer session another tab stored where it is
  test("a 401 for this tab's token leaves the session another tab stored alone", async () => {
    localStorage.setItem('user', JSON.stringify(session))
    const otherTab = { ...session, accessToken: 'jwt2' }
    let answerMe: (value: unknown) => void = () => {}
    // the mount's GET /users/me, left hanging
    fetchMock.mockResolvedValue(me).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          answerMe = resolve
        }),
    )

    renderAt('/profile')
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    // the other tab signs out and back in: storage holds its session now
    localStorage.setItem('user', JSON.stringify(otherTab))

    // only this tab's older token was refused
    await act(async () => {
      answerMe(problem(401, 'Token has been revoked'))
    })

    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(otherTab)
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
  })

  // PR #182 re-review (@Sinnez1): login()'s setToken writes storage at
  // once but shows up in the rendered token only after React commits, so
  // a 401 for the old token landing in that one-render window used to
  // pass the guard and clear the session just stored
  test('a 401 for the old token during login leaves the new session alone', async () => {
    const user = userEvent.setup()
    localStorage.setItem('user', JSON.stringify(session))
    const newSession = { ...session, accessToken: 'jwt2' }
    let answerStaleMe: (value: unknown) => void = () => {}
    fetchMock.mockImplementation((url: string, init: RequestInit) => {
      if (String(url).endsWith('/auth/login')) {
        // the refusal is timed off the login body being read, as the
        // review's repro does: it lands after login() has stored the new
        // session and before the render that would show it
        return Promise.resolve({
          ...reply(200, newSession),
          json: async () => {
            answerStaleMe(problem(401, 'Token has been revoked'))
            return newSession
          },
        })
      }
      // the stale token's account check hangs; the new one's answers
      return new Headers(init.headers).get('Authorization') === 'Bearer jwt'
        ? new Promise((resolve) => {
            answerStaleMe = resolve
          })
        : Promise.resolve(me)
    })

    renderAt('/login')
    await user.type(screen.getByLabelText('Username or Email'), 'alex')
    await user.type(screen.getByLabelText('Password'), 'ValidPassword123')
    await user.click(submitButton('Log In'))

    await screen.findByRole('button', { name: 'Logout' })
    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(
      newSession,
    )
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
  })

  // owner decision via options Q&A (2026-10-14) on PR #182 re-review
  // (@Sinnez1): a stored token that has expired is dropped wherever the
  // visitor is, but a page that needs no session is not left behind —
  // ProtectedRoute is what takes a gated page to /login
  test.each([
    ['/', 'Welcome!'],
    ['/register', 'Create Account'],
  ])(
    'a dead stored token is dropped in place on %s, with no redirect and no notice',
    async (path, heading) => {
      localStorage.setItem('user', JSON.stringify(session))
      fetchMock.mockResolvedValue(problem(401, 'Token has expired'))

      renderAt(path)

      await waitFor(() => expect(localStorage.getItem('user')).toBe('null'))
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
      expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
      // nor is a notice saved up for the next visit to the login page
      cleanup()
      renderAt('/login')
      expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
    },
  )

  // PR #182 re-review (@Sinnez1): the provider's last closure stayed
  // registered after it unmounted, so a late 401 acted on a session it
  // no longer owned — across tests too, which reuse this token
  test('an unmounted provider ignores a late 401', async () => {
    localStorage.setItem('user', JSON.stringify(session))
    let answerMe: (value: unknown) => void = () => {}
    fetchMock.mockResolvedValue(me).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          answerMe = resolve
        }),
    )

    renderAt('/profile')
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    cleanup()

    // the same token is still stored, so only the unregistering stands
    // between that reply and this session
    await act(async () => {
      answerMe(problem(401, 'Token has been revoked'))
    })

    expect(JSON.parse(localStorage.getItem('user') ?? 'null')).toEqual(session)
    renderAt('/login')
    expect(screen.queryByText(SESSION_ENDED)).not.toBeInTheDocument()
  })
})
