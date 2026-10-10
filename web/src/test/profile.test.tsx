// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: tests for the My Profile page — details card, edit-account OTP
// flow, change password, delete account — against a spied profileApi,
// following admin.test.tsx's harness (memory router over the real route
// table, stored session seeded to pass ProtectedRoute).
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): rewritten for
// #92's real endpoints (PR #157) — the 202 email-change branch and its
// dialog, the localStorage snapshot that survives a reload, the
// problem+json type URIs that decide whether a card retries, amends or
// restarts, and the password call's { newPassword, confirmPassword, otp }.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: cases for the
// three fixes — a pending snapshot is scoped to the account that parked it
// (another account's is neither shown nor acted on), a retryable failure
// after an amended save brings the code boxes back, and an untyped 400
// (the server's own body validation) returns both cards to their fields
// with the unspent code kept.
// Same review, second pass: the retained-code retries assert the call
// COUNT, since their arguments repeat the refused attempt's and passed
// whether or not the second Save ran; and an expired email change offers
// a restart instead of the resend user-service can only refuse.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): the page
// reads the session's one GET /users/me (adminUserApi, through
// AuthProvider) rather than fetching its own, so that is what the
// harness fakes; plus cases for the dialog that cannot be dismissed
// mid-delete, an edit reaching the rest of the app, the 429 wait on the
// code step, and a kept code that has since expired.
// Author review: Leong Wei Zhi (via PR #149).

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { adminUserApi } from '@/features/user/adminApi'
import { EmailChangeModal } from '@/features/user/components/EmailChangeModal'
import { profileApi } from '@/features/user/profileApi'
import {
  OTP_ATTEMPTS_EXCEEDED,
  OTP_INVALID,
  OTP_RESEND_COOLDOWN,
  USERNAME_TAKEN,
} from '@/features/user/problemTypes'
import type { PendingEmailChange } from '@/features/user/types'
import type { AdminUser } from '@/features/user/types'
import { ApiError } from '@/lib/api/http'
import { routes } from '../routes'

const OTP = '000000'
const NEW_EMAIL = 'e9999999@u.nus.edu'

let me: AdminUser

function renderProfile() {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/profile'] })}
    />,
  )
}

// user-service's problem+json refusals, as apiFetch throws them.
function refusal(status: number, detail: string, type?: string, wait?: number) {
  return new ApiError(status, { status, detail, type }, wait ?? null)
}

beforeEach(() => {
  vi.restoreAllMocks()
  // /profile sits behind ProtectedRoute, which only checks that
  // AuthProvider found a stored session under "user".
  localStorage.setItem('user', JSON.stringify({ accessToken: 'test-token' }))
  me = {
    id: 3,
    email: 'e1000101@u.nus.edu',
    username: 'nus_courier_99',
    role: 'USER',
    createdAt: '2026-09-03T08:00:00Z',
  }
  // the page reads the session's GET /users/me, which AuthProvider
  // fetches once through adminUserApi (PR #149 review, @Sinnez1)
  vi.spyOn(adminUserApi, 'getCurrentUser').mockImplementation(async () => ({
    ...me,
  }))
  vi.spyOn(profileApi, 'requestOtp').mockResolvedValue({
    expiresInSeconds: 600,
    resendInSeconds: 60,
  })
  vi.spyOn(profileApi, 'updateAccount').mockImplementation(
    async (changes, otp) => {
      if (otp !== OTP) throw refusal(400, 'Invalid code', OTP_INVALID)
      me = { ...me, ...changes }
      return { kind: 'applied', user: { ...me } }
    },
  )
  vi.spyOn(profileApi, 'verifyEmailChange').mockImplementation(async () => {
    me = { ...me, email: NEW_EMAIL }
    return { ...me }
  })
  vi.spyOn(profileApi, 'resendEmailChange').mockResolvedValue({
    user: { ...me },
    email: NEW_EMAIL,
    expiresInSeconds: 600,
    resendInSeconds: 60,
  })
  vi.spyOn(profileApi, 'changePassword').mockResolvedValue(undefined)
  vi.spyOn(profileApi, 'deleteAccount').mockResolvedValue(undefined)
})

afterEach(() => {
  localStorage.clear()
})

// Both cards carry a "Verify & Continue" button and a set of code boxes,
// so every interaction is scoped to the card it belongs to.
const card = (heading: string) =>
  within(screen.getByRole('heading', { name: heading }).closest('section')!)

const editCard = () => card('Edit Account Info')
const passwordCard = () => card('Change Password')

async function openEditCard(user: ReturnType<typeof userEvent.setup>) {
  renderProfile()
  await user.click(await screen.findByRole('button', { name: 'Edit Profile' }))
  return editCard().getByRole('textbox', { name: 'Desired Username' })
}

// the boxes advance focus themselves, so one keyboard burst fills them
async function typeCode(
  user: ReturnType<typeof userEvent.setup>,
  code: string,
  scope: { getByLabelText: typeof screen.getByLabelText },
) {
  await user.click(scope.getByLabelText('Digit 1 of 6'))
  await user.keyboard(code)
}

const snapshot = () =>
  JSON.parse(localStorage.getItem('pendingEmailChange') ?? 'null')

// the localStorage snapshot of a parked email change, as the page writes
// it: user-service has no GET for the row
function storeSnapshot(override: Partial<PendingEmailChange> = {}) {
  localStorage.setItem(
    'pendingEmailChange',
    JSON.stringify({
      userId: me.id,
      email: NEW_EMAIL,
      expiresAt: Date.now() + 600_000,
      resendAt: Date.now() + 60_000,
      ...override,
    }),
  )
}

// What Spring answers with when a request body breaks the DTO's own rules
// (ProblemDetailAdvice.handleInvalidBody): a 400 whose `type` is the
// placeholder `about:blank`, i.e. no type at all. The code never reached
// the gate, so it is still live and the FIELDS are what need fixing.
const UNTYPED = 'about:blank'

describe('profile page', () => {
  it('redirects to the login page without a session', async () => {
    localStorage.clear()
    renderProfile()
    expect(
      await screen.findByRole('heading', { name: 'Log In' }),
    ).toBeInTheDocument()
  })

  it('shows the profile details with placeholder credits', async () => {
    renderProfile()
    expect(await screen.findByText('nus_courier_99')).toBeInTheDocument()
    expect(screen.getByText('e1000101@u.nus.edu')).toBeInTheDocument()
    expect(screen.getByText('Requester / Courier')).toBeInTheDocument()
    expect(screen.getByText('Available: — | Reserved: —')).toBeInTheDocument()
  })

  it('shows an error when the profile fails to load', async () => {
    vi.mocked(adminUserApi.getCurrentUser).mockRejectedValue(new Error('boom'))
    renderProfile()
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
  })
})

describe('edit account info', () => {
  it('updates the username after OTP verification', async () => {
    const user = userEvent.setup()
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'utown_runner')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    expect(profileApi.requestOtp).toHaveBeenCalled()

    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Edit Account Info' }),
      ).not.toBeInTheDocument(),
    )
    expect(profileApi.updateAccount).toHaveBeenCalledWith(
      { username: 'utown_runner' },
      OTP,
    )
    // the session's GET /users/me, fetched once by AuthProvider and
    // written back by the save: the name on screen is that shared copy,
    // which the nav bar and the /admin guard read too (PR #149 review)
    expect(screen.getByText('utown_runner')).toBeInTheDocument()
    expect(adminUserApi.getCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('rejects verification when nothing changed', async () => {
    const user = userEvent.setup()
    await openEditCard(user)
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Nothing to change.',
    )
    expect(profileApi.requestOtp).not.toHaveBeenCalled()
  })

  it('reuses the still-live code when a username is refused as taken', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount).mockRejectedValueOnce(
      refusal(400, 'Username is already taken', USERNAME_TAKEN),
    )
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'taken_name')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // the refusal rolled the gate code's consumption back with it, so the
    // card edits again holding a code that still works
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Username is already taken',
    )
    expect(
      screen.getByRole('textbox', { name: 'Desired Username' }),
    ).toBeEnabled()
    expect(
      editCard().getByText(/still valid — Save to use it again/),
    ).toBeInTheDocument()

    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // the same code again, and no new one asked for. The call count is
    // what proves the second Save ran at all: its arguments match the
    // refused first one (PR #149 review)
    await waitFor(() =>
      expect(profileApi.updateAccount).toHaveBeenCalledTimes(2),
    )
    expect(profileApi.updateAccount).toHaveBeenLastCalledWith(
      { username: 'taken_name' },
      OTP,
    )
    expect(profileApi.requestOtp).toHaveBeenCalledTimes(1)
  })

  it('brings the code boxes back when a save after an amend fails', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount)
      .mockRejectedValueOnce(
        refusal(400, 'Username is already taken', USERNAME_TAKEN),
      )
      // the retry from the fields, where the amend left the card: a mail
      // 502 or a 500 never judged the code, so it is still live
      .mockRejectedValueOnce(refusal(502, 'Could not send the email', UNTYPED))
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'taken_name')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // amended: back at the fields, holding the code
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Username is already taken',
    )
    await user.clear(
      editCard().getByRole('textbox', { name: 'Desired Username' }),
    )
    await user.type(
      editCard().getByRole('textbox', { name: 'Desired Username' }),
      'utown_runner',
    )
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // the digits were cleared by the failure, so the step has to come
    // back with them — Save from the fields would send an empty code
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Could not send the email',
    )
    expect(editCard().getByLabelText('Digit 1 of 6')).toHaveValue('')
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // three saves, not two: the retyped code went out on its own call,
    // whose arguments match the 502'd one (PR #149 review)
    await waitFor(() =>
      expect(profileApi.updateAccount).toHaveBeenCalledTimes(3),
    )
    expect(profileApi.updateAccount).toHaveBeenLastCalledWith(
      { username: 'utown_runner' },
      OTP,
    )
    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Edit Account Info' }),
      ).not.toBeInTheDocument(),
    )
    // one code throughout: nothing here was worth a resend
    expect(profileApi.requestOtp).toHaveBeenCalledTimes(1)
  })

  it('returns to the fields with the code when the server refuses the body', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount).mockRejectedValueOnce(
      refusal(400, 'Username must be 3-30 characters.', UNTYPED),
    )
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'ab')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // the validation ran before the code was read, so the fields — the
    // part that was refused — are editable again and the code still works
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Username must be 3-30 characters.',
    )
    expect(
      editCard().getByRole('textbox', { name: 'Desired Username' }),
    ).toBeEnabled()
    expect(
      editCard().getByText(/still valid — Save to use it again/),
    ).toBeInTheDocument()

    await user.clear(
      editCard().getByRole('textbox', { name: 'Desired Username' }),
    )
    await user.type(
      editCard().getByRole('textbox', { name: 'Desired Username' }),
      'utown_runner',
    )
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(profileApi.updateAccount).toHaveBeenCalledTimes(2),
    )
    expect(profileApi.updateAccount).toHaveBeenLastCalledWith(
      { username: 'utown_runner' },
      OTP,
    )
    expect(profileApi.requestOtp).toHaveBeenCalledTimes(1)
  })

  it('counts down the quoted wait when the save is refused for now', async () => {
    const user = userEvent.setup()
    // a PATCH that parks an email change inside a previous change's
    // cooldown: refused for now, and the code's consumption rolled back
    // with it, so the digits in the boxes are still good
    vi.mocked(profileApi.updateAccount).mockRejectedValueOnce(
      refusal(429, 'A code was sent moments ago', OTP_RESEND_COOLDOWN, 30),
    )
    await openEditCard(user)

    const emailInput = editCard().getByRole('textbox', { name: 'NUS Email' })
    await user.clear(emailInput)
    await user.type(emailInput, NEW_EMAIL)
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    const retry = await editCard().findByRole('button', {
      name: /Try again in \d+s/,
    })
    expect(retry).toBeDisabled()
    // still on the code step, still holding the code it typed
    expect(editCard().getByLabelText('Digit 1 of 6')).toHaveValue('0')
    expect(profileApi.requestOtp).toHaveBeenCalledTimes(1)
  })

  it('asks for a new code when the one it kept has expired', async () => {
    const user = userEvent.setup()
    // a resend late in the window leaves almost nothing: this code dies
    // as it arrives, so the card must not offer it after the refusal
    vi.mocked(profileApi.requestOtp).mockResolvedValue({
      expiresInSeconds: 0,
      resendInSeconds: 0,
    })
    vi.mocked(profileApi.updateAccount).mockRejectedValueOnce(
      refusal(400, 'Username is already taken', USERNAME_TAKEN),
    )
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'taken_name')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    // amended back to the fields, but the kept code is dead: no promise
    // that it still works, and the button asks for a new one
    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Username is already taken',
    )
    expect(
      editCard().queryByText(/still valid — Save to use it again/),
    ).not.toBeInTheDocument()
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await waitFor(() => expect(profileApi.requestOtp).toHaveBeenCalledTimes(2))
  })

  it('sends the card back and quotes the wait once the attempts are spent', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount).mockRejectedValue(
      refusal(429, 'Too many incorrect codes', OTP_ATTEMPTS_EXCEEDED, 45),
    )
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'utown_runner')
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    expect(await editCard().findByRole('alert')).toHaveTextContent(
      'Too many incorrect codes',
    )
    const retry = await editCard().findByRole('button', {
      name: /Try again in \d+s/,
    })
    expect(retry).toBeDisabled()
  })

  it('parks an email change and applies it from the confirm dialog', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount).mockResolvedValueOnce({
      kind: 'emailPending',
      pending: {
        user: { ...me },
        email: NEW_EMAIL,
        expiresInSeconds: 600,
        resendInSeconds: 60,
      },
    })
    await openEditCard(user)

    const emailInput = editCard().getByRole('textbox', { name: 'NUS Email' })
    await user.clear(emailInput)
    await user.type(emailInput, NEW_EMAIL)
    await user.click(
      editCard().getByRole('button', { name: 'Verify & Continue' }),
    )
    await typeCode(user, OTP, editCard())
    await user.click(editCard().getByRole('button', { name: 'Save' }))

    const dialog = within(
      await screen.findByRole('dialog', { name: 'Confirm New Email' }),
    )
    expect(dialog.getByText(new RegExp(NEW_EMAIL))).toBeInTheDocument()
    expect(snapshot().email).toBe(NEW_EMAIL)

    await typeCode(user, OTP, dialog)
    await user.click(dialog.getByRole('button', { name: 'Verify & Apply' }))

    expect(await screen.findByText(NEW_EMAIL)).toBeInTheDocument()
    expect(profileApi.verifyEmailChange).toHaveBeenCalledWith(OTP)
    expect(snapshot()).toBeNull()
  })

  it('still offers the code step after a reload mid-confirm', async () => {
    const user = userEvent.setup()
    // user-service has no GET for the parked row, so the page remembers it
    storeSnapshot()
    renderProfile()

    await user.click(
      await screen.findByRole('button', { name: 'Enter your code' }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Confirm New Email' }),
    )
    await typeCode(user, OTP, dialog)
    await user.click(dialog.getByRole('button', { name: 'Verify & Apply' }))

    expect(await screen.findByText(NEW_EMAIL)).toBeInTheDocument()
    expect(profileApi.updateAccount).not.toHaveBeenCalled()
  })

  it('ignores a pending change parked by another account', async () => {
    // localStorage is browser-wide and a snapshot outlives the session
    // that wrote it: this one is account 99's, and account 3 is signed in
    storeSnapshot({ userId: 99 })
    renderProfile()

    await screen.findByText('nus_courier_99')
    expect(
      screen.queryByRole('button', { name: 'Enter your code' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText(new RegExp(NEW_EMAIL))).not.toBeInTheDocument()
    // left where it is: still its owner's change to finish
    expect(snapshot().userId).toBe(99)
  })

  it('ignores a pending change that names no account', async () => {
    // written before snapshots carried an owner: nobody can claim it
    storeSnapshot({ userId: undefined })
    renderProfile()

    await screen.findByText('nus_courier_99')
    expect(
      screen.queryByRole('button', { name: 'Enter your code' }),
    ).not.toBeInTheDocument()
    await waitFor(() => expect(snapshot()).toBeNull())
  })

  // Rendered directly, with a dead snapshot: the page's own sweep clears
  // those on mount, so the dialog meets one only when the change dies
  // while the page is open — either as it sits open (CodeStep's tick
  // re-renders it) or when "Enter your code" is clicked afterwards.
  it('offers a restart instead of a doomed resend once the change expires', async () => {
    const user = userEvent.setup()
    const onDiscarded = vi.fn()
    render(
      <EmailChangeModal
        pending={{
          userId: 3,
          email: NEW_EMAIL,
          expiresAt: Date.now() - 1_000,
          resendAt: Date.now() - 1_000,
        }}
        onClose={vi.fn()}
        onVerified={vi.fn()}
        onResent={vi.fn()}
        onDiscarded={onDiscarded}
      />,
    )

    // user-service deletes an expired pending_email_changes row and
    // answers OTP_EXPIRED, so neither button could do anything but fail
    expect(
      screen.queryByRole('button', { name: 'Resend code' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Digit 1 of 6')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Start over' }))

    expect(onDiscarded).toHaveBeenCalledWith(
      expect.stringContaining('expired before it was confirmed'),
    )
    expect(profileApi.resendEmailChange).not.toHaveBeenCalled()
    expect(profileApi.verifyEmailChange).not.toHaveBeenCalled()
  })

  it('discards changes on cancel', async () => {
    const user = userEvent.setup()
    const usernameInput = await openEditCard(user)
    await user.clear(usernameInput)
    await user.type(usernameInput, 'someone_else')
    await user.click(editCard().getByRole('button', { name: 'Cancel' }))

    expect(
      screen.queryByRole('heading', { name: 'Edit Account Info' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('nus_courier_99')).toBeInTheDocument()
  })
})

describe('change password', () => {
  async function fillPasswords(
    user: ReturnType<typeof userEvent.setup>,
    next: string,
    confirm: string,
  ) {
    await user.type(passwordCard().getByLabelText('New Password'), next)
    await user.type(
      passwordCard().getByLabelText('Confirm New Password'),
      confirm,
    )
    await user.click(
      passwordCard().getByRole('button', { name: 'Verify & Continue' }),
    )
  }

  it('blocks mismatched passwords without calling the API', async () => {
    const user = userEvent.setup()
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'NewPassword1', 'Different1')
    expect(await passwordCard().findByRole('alert')).toHaveTextContent(
      'New passwords do not match.',
    )
    expect(profileApi.requestOtp).not.toHaveBeenCalled()
  })

  it('sends the confirmation and the code to the server', async () => {
    const user = userEvent.setup()
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'NewPassword1', 'NewPassword1')
    expect(profileApi.requestOtp).toHaveBeenCalled()

    await typeCode(user, OTP, passwordCard())
    await user.click(
      passwordCard().getByRole('button', { name: 'Update Password' }),
    )

    expect(
      await passwordCard().findByText('Password updated.'),
    ).toBeInTheDocument()
    // the double-entry check is the server's (F2.1.4), so the confirmation
    // travels with the request instead of dying in the form
    expect(profileApi.changePassword).toHaveBeenCalledWith(
      'NewPassword1',
      'NewPassword1',
      OTP,
    )
    expect(passwordCard().getByLabelText('New Password')).toHaveValue('')
  })

  it('returns to the fields with the code when the password is refused', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.changePassword).mockRejectedValueOnce(
      refusal(400, 'Password must be 10-50 characters.', UNTYPED),
    )
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'short1', 'short1')
    await typeCode(user, OTP, passwordCard())
    await user.click(
      passwordCard().getByRole('button', { name: 'Update Password' }),
    )

    // the policy check ran before the code was read: the password is
    // editable again, and the unspent code is kept
    expect(await passwordCard().findByRole('alert')).toHaveTextContent(
      'Password must be 10-50 characters.',
    )
    expect(passwordCard().getByLabelText('New Password')).toBeEnabled()
    expect(
      passwordCard().getByText(/still valid — continue to use it again/),
    ).toBeInTheDocument()

    await user.clear(passwordCard().getByLabelText('New Password'))
    await user.clear(passwordCard().getByLabelText('Confirm New Password'))
    await fillPasswords(user, 'NewPassword1', 'NewPassword1')
    await user.click(
      passwordCard().getByRole('button', { name: 'Update Password' }),
    )

    expect(
      await passwordCard().findByText('Password updated.'),
    ).toBeInTheDocument()
    expect(profileApi.changePassword).toHaveBeenCalledTimes(2)
    expect(profileApi.changePassword).toHaveBeenLastCalledWith(
      'NewPassword1',
      'NewPassword1',
      OTP,
    )
    // fixing the password cost no resend
    expect(profileApi.requestOtp).toHaveBeenCalledTimes(1)
  })

  it('keeps the code step when the code is wrong', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.changePassword).mockRejectedValue(
      refusal(400, 'Invalid verification code', OTP_INVALID),
    )
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'NewPassword1', 'NewPassword1')
    await typeCode(user, OTP, passwordCard())
    await user.click(
      passwordCard().getByRole('button', { name: 'Update Password' }),
    )

    expect(await passwordCard().findByRole('alert')).toHaveTextContent(
      'Invalid verification code',
    )
    expect(passwordCard().getByLabelText('Digit 1 of 6')).toBeInTheDocument()
  })
})

describe('delete account', () => {
  async function openDeleteModal(user: ReturnType<typeof userEvent.setup>) {
    renderProfile()
    await screen.findByText('nus_courier_99')
    await user.click(screen.getByRole('button', { name: 'Delete Account...' }))
    return screen.getByRole('dialog', { name: 'Are you sure?' })
  }

  it('warns about the 30-day lock before deleting', async () => {
    const user = userEvent.setup()
    const dialog = await openDeleteModal(user)
    expect(dialog).toHaveTextContent(/locked for 30 days/)
  })

  it('keeps the session on cancel', async () => {
    const user = userEvent.setup()
    await openDeleteModal(user)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(
      screen.queryByRole('dialog', { name: 'Are you sure?' }),
    ).not.toBeInTheDocument()
    expect(profileApi.deleteAccount).not.toHaveBeenCalled()
    expect(JSON.parse(localStorage.getItem('user')!)).not.toBeNull()
  })

  it('deletes the account and ends the session', async () => {
    const user = userEvent.setup()
    await openDeleteModal(user)
    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))

    // Logging out from a protected page lands on the login page:
    // ProtectedRoute's redirect wins over logout()'s navigation home
    // (same as every other logout from a protected page).
    expect(
      await screen.findByRole('heading', { name: 'Log In' }),
    ).toBeInTheDocument()
    expect(profileApi.deleteAccount).toHaveBeenCalled()
    expect(JSON.parse(localStorage.getItem('user')!)).toBeNull()
  })

  it('cannot be dismissed while the delete is in flight', async () => {
    const user = userEvent.setup()
    let finish = () => {}
    vi.mocked(profileApi.deleteAccount).mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    const dialog = await openDeleteModal(user)
    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))
    await screen.findByRole('button', { name: 'Deleting...' })

    // the DELETE is away and logout() follows it: closing here would
    // sign the user out with no warning, and strand a failure's message
    // on a dialog that is gone (PR #149 review, @Sinnez1)
    const cancel = screen.getByRole('button', { name: 'Cancel' })
    expect(cancel).toBeDisabled()
    await user.click(cancel)
    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(dialog).toBeInTheDocument()

    finish()
    expect(
      await screen.findByRole('heading', { name: 'Log In' }),
    ).toBeInTheDocument()
  })

  it('keeps the modal open when deletion fails', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.deleteAccount).mockRejectedValue(new Error('boom'))
    const dialog = await openDeleteModal(user)
    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(dialog).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('user')!)).not.toBeNull()
  })
})
