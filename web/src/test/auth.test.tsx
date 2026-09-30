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

function reply(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
) {
  return {
    ok: status < 400,
    status,
    headers: new Headers(headers),
    json: async () => body,
  }
}

function problem(
  status: number,
  detail: string,
  headers?: Record<string, string>,
) {
  return reply(status, { status, detail }, headers)
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
    ],
    [
      'too many wrong codes',
      429,
      'Too many incorrect codes; sign up again to get a new code',
    ],
  ])(
    '%s sends the user back to the filled form',
    async (_case, status, detail) => {
      fetchMock
        .mockResolvedValueOnce(reply(202, accepted))
        .mockResolvedValueOnce(problem(status, detail))

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
