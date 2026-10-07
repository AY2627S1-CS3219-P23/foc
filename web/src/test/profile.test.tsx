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
// Reviewed by: [pending]

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { profileApi } from '@/features/user/profileApi'
import {
  OTP_ATTEMPTS_EXCEEDED,
  OTP_INVALID,
  USERNAME_TAKEN,
} from '@/features/user/problemTypes'
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
  vi.spyOn(profileApi, 'getCurrentUser').mockImplementation(async () => ({
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
    vi.mocked(profileApi.getCurrentUser).mockRejectedValue(new Error('boom'))
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
    expect(screen.getByText('utown_runner')).toBeInTheDocument()
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
    await waitFor(() => expect(profileApi.requestOtp).toHaveBeenCalledTimes(1))
    expect(profileApi.updateAccount).toHaveBeenLastCalledWith(
      { username: 'taken_name' },
      OTP,
    )
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
    localStorage.setItem(
      'pendingEmailChange',
      JSON.stringify({
        email: NEW_EMAIL,
        expiresAt: Date.now() + 600_000,
        resendAt: Date.now() + 60_000,
      }),
    )
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
