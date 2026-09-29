// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: own-profile endpoints of user-service through the shared
// apiFetch wrapper — GET /users/me and DELETE /users/me are real today;
// the OTP/update/password calls are a PROVISIONAL contract for #92 (see
// below). Dev-only mock switch copied from adminApi.ts.
// Reviewed by: [pending]

import { apiFetch } from '@/lib/api/http'
import type { AdminUser } from './types'

export interface AccountChanges {
  username?: string
  email?: string
}

// PROVISIONAL (#92): user-service has no account-update endpoints yet.
// The shapes below (POST /users/me/otp, PATCH /users/me with the OTP in
// the body, POST /users/me/password) are this page's proposal, to be
// renegotiated with #92's owner; swapping in the real contract should
// touch only this file (and the mock, which dies with #92). Until then,
// without the mock flag these calls 404/405 into the cards' error boxes.
// F2.1.4's second OTP to the *new* email is deferred to #92 as well.
const httpProfileApi = {
  getCurrentUser(): Promise<AdminUser> {
    return apiFetch<AdminUser>('user', '/users/me')
  },

  deleteAccount(): Promise<void> {
    return apiFetch<void>('user', '/users/me', { method: 'DELETE' })
  },

  // Sends an OTP to the account's current email.
  requestOtp(): Promise<void> {
    return apiFetch<void>('user', '/users/me/otp', { method: 'POST' })
  },

  updateAccount(changes: AccountChanges, otp: string): Promise<AdminUser> {
    return apiFetch<AdminUser>('user', '/users/me', {
      method: 'PATCH',
      body: JSON.stringify({ ...changes, otp }),
    })
  },

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return apiFetch<void>('user', '/users/me/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  },
}

// TEMPORARY: VITE_MOCK_PROFILE_API=true (in web/.env.local) swaps in the
// in-memory mock so the edit flows can be walked before #92 lands. A
// separate flag from VITE_MOCK_ADMIN_API so each mock retires on its own
// schedule. Dev server only: production builds always use the real calls.
// The mock is imported on first use, so production bundles leave it out.
function loadMock() {
  return import('./profileApiMock').then((m) => m.mockProfileApi)
}

const devMockProfileApi: typeof httpProfileApi = {
  getCurrentUser: async () => (await loadMock()).getCurrentUser(),
  deleteAccount: async () => (await loadMock()).deleteAccount(),
  requestOtp: async () => (await loadMock()).requestOtp(),
  updateAccount: async (changes, otp) =>
    (await loadMock()).updateAccount(changes, otp),
  changePassword: async (current, next) =>
    (await loadMock()).changePassword(current, next),
}

export const profileApi: typeof httpProfileApi =
  import.meta.env.DEV && import.meta.env.VITE_MOCK_PROFILE_API === 'true'
    ? devMockProfileApi
    : httpProfileApi
