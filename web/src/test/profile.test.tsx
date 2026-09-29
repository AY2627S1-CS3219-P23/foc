// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: tests for the My Profile page — details card, edit-account OTP
// flow, change password, delete account — against a spied profileApi,
// following admin.test.tsx's harness (memory router over the real route
// table, stored session seeded to pass ProtectedRoute).
// Reviewed by: [pending]

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { profileApi } from '@/features/user/profileApi'
import type { AdminUser } from '@/features/user/types'
import { routes } from '../routes'

const OTP = '000000'

let me: AdminUser

function renderProfile() {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/profile'] })}
    />,
  )
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
  vi.spyOn(profileApi, 'requestOtp').mockResolvedValue(undefined)
  vi.spyOn(profileApi, 'updateAccount').mockImplementation(
    async (changes, otp) => {
      if (otp !== OTP) throw new Error('Invalid or expired OTP.')
      me = { ...me, ...changes }
      return { ...me }
    },
  )
  vi.spyOn(profileApi, 'changePassword').mockResolvedValue(undefined)
  vi.spyOn(profileApi, 'deleteAccount').mockResolvedValue(undefined)
})

afterEach(() => {
  localStorage.clear()
})

async function openEditCard(user: ReturnType<typeof userEvent.setup>) {
  renderProfile()
  await user.click(
    await screen.findByRole('button', { name: 'Edit Profile' }),
  )
  return screen.getByRole('textbox', { name: 'Desired Username' })
}

async function typeOtp(user: ReturnType<typeof userEvent.setup>, otp: string) {
  for (let i = 0; i < otp.length; i++) {
    await user.type(screen.getByLabelText(`OTP digit ${i + 1}`), otp[i])
  }
}

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
    vi.mocked(profileApi.getCurrentUser).mockRejectedValue(
      new Error('boom'),
    )
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
    await user.click(screen.getByRole('button', { name: 'Verify & Continue' }))
    expect(profileApi.requestOtp).toHaveBeenCalled()

    await typeOtp(user, OTP)
    await user.click(screen.getByRole('button', { name: 'Save' }))

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
    await user.click(screen.getByRole('button', { name: 'Verify & Continue' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Nothing to change.',
    )
    expect(profileApi.requestOtp).not.toHaveBeenCalled()
  })

  it('shows the server error when saving fails', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.updateAccount).mockRejectedValue(
      new Error('Username is already taken.'),
    )
    const usernameInput = await openEditCard(user)

    await user.clear(usernameInput)
    await user.type(usernameInput, 'taken_name')
    await user.click(screen.getByRole('button', { name: 'Verify & Continue' }))
    await typeOtp(user, OTP)
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Username is already taken.',
    )
    expect(
      screen.getByRole('heading', { name: 'Edit Account Info' }),
    ).toBeInTheDocument()
  })

  it('discards changes on cancel', async () => {
    const user = userEvent.setup()
    const usernameInput = await openEditCard(user)
    await user.clear(usernameInput)
    await user.type(usernameInput, 'someone_else')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(
      screen.queryByRole('heading', { name: 'Edit Account Info' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('nus_courier_99')).toBeInTheDocument()
  })
})

describe('change password', () => {
  async function fillPasswords(
    user: ReturnType<typeof userEvent.setup>,
    current: string,
    next: string,
    confirm: string,
  ) {
    await user.type(screen.getByLabelText('Current Password'), current)
    await user.type(screen.getByLabelText('New Password'), next)
    await user.type(screen.getByLabelText('Confirm New Password'), confirm)
    await user.click(screen.getByRole('button', { name: 'Update Password' }))
  }

  it('blocks mismatched passwords without calling the API', async () => {
    const user = userEvent.setup()
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'OldPassword1', 'NewPassword1', 'Different1')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'New passwords do not match.',
    )
    expect(profileApi.changePassword).not.toHaveBeenCalled()
  })

  it('updates the password and clears the form', async () => {
    const user = userEvent.setup()
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'OldPassword1', 'NewPassword1', 'NewPassword1')

    expect(await screen.findByText('Password updated.')).toBeInTheDocument()
    expect(profileApi.changePassword).toHaveBeenCalledWith(
      'OldPassword1',
      'NewPassword1',
    )
    expect(screen.getByLabelText('New Password')).toHaveValue('')
  })

  it('shows the server error when the update fails', async () => {
    const user = userEvent.setup()
    vi.mocked(profileApi.changePassword).mockRejectedValue(
      new Error('Current password is incorrect.'),
    )
    renderProfile()
    await screen.findByText('nus_courier_99')
    await fillPasswords(user, 'WrongPassword1', 'NewPassword1', 'NewPassword1')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Current password is incorrect.',
    )
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
    await user.click(
      screen.getByRole('button', { name: 'Delete My Account' }),
    )

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
    await user.click(
      screen.getByRole('button', { name: 'Delete My Account' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(dialog).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('user')!)).not.toBeNull()
  })
})
