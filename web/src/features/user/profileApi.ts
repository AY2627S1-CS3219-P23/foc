// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: own-profile endpoints of user-service through the shared
// apiFetch wrapper.
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): #92's account
// update flows landed (PR #157), so the PROVISIONAL contract this file
// held — and the dev-only mock behind VITE_MOCK_PROFILE_API that let the
// screens be walked without it — are gone. Every call here is real, and
// the shapes mirror user-service's DTOs (UpdateOtpResponse,
// UpdateAccountRequest, EmailChangePendingResponse,
// VerifyEmailChangeRequest, ChangePasswordRequest); see
// user-service/README.md for the routes and their problem+json types.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1):
// getCurrentUser is gone — GET /users/me is fetched once per session by
// AuthProvider (adminApi.getCurrentUser) and shared as useAuth().me, so
// a second wrapper here only bought the profile page a duplicate request
// and a private copy that drifted from everyone else's.
// Author review: Leong Wei Zhi (via PR #149).

import { apiFetch } from '@/lib/api/http'
import type { AdminUser, EmailChangeAccepted, OtpTimings } from './types'

export interface AccountChanges {
  username?: string
  email?: string
}

// PATCH /users/me answers two ways: 200 with the account when everything
// applied, or 202 with a parked email change waiting on a code sent to the
// new address.
export type UpdateAccountResult =
  | { kind: 'applied'; user: AdminUser }
  | { kind: 'emailPending'; pending: EmailChangeAccepted }

// apiFetch returns the parsed body and drops the status, so the two
// answers are told apart by shape: the 202 body NESTS the account under
// `user`, the 200 body IS the account. Sniffing one field here beat
// widening the shared wrapper with a status-returning variant for this
// single caller.
function isEmailPending(
  body: AdminUser | EmailChangeAccepted,
): body is EmailChangeAccepted {
  return 'user' in body
}

export const profileApi = {
  deleteAccount(): Promise<void> {
    return apiFetch<void>('user', '/users/me', { method: 'DELETE' })
  },

  // The gate code (F2.1.1): single-use, sent to the account's CURRENT
  // email, and required by every change below. One row per account, so
  // repeating this call is the resend — and both cards on the page draw
  // on the same code.
  requestOtp(): Promise<OtpTimings> {
    return apiFetch<OtpTimings>('user', '/users/me/otp', { method: 'POST' })
  },

  async updateAccount(
    changes: AccountChanges,
    otp: string,
  ): Promise<UpdateAccountResult> {
    const body = await apiFetch<AdminUser | EmailChangeAccepted>(
      'user',
      '/users/me',
      { method: 'PATCH', body: JSON.stringify({ ...changes, otp }) },
    )
    return isEmailPending(body)
      ? { kind: 'emailPending', pending: body }
      : { kind: 'applied', user: body }
  },

  // Confirms the parked change with the code sent to the NEW address
  // (F2.1.3); answers with the account, new email applied.
  verifyEmailChange(code: string): Promise<AdminUser> {
    return apiFetch<AdminUser>('user', '/users/me/email/verify', {
      method: 'POST',
      body: JSON.stringify({ code }),
    })
  },

  // Its own route rather than a repeat of the PATCH: the gate code was
  // consumed when the change parked, so re-sending to an address the
  // already-gated PATCH chose needs no second gate.
  resendEmailChange(): Promise<EmailChangeAccepted> {
    return apiFetch<EmailChangeAccepted>('user', '/users/me/email/resend', {
      method: 'POST',
    })
  },

  // No currentPassword: the gate code is the authentication, and the
  // double-entry check (F2.1.4) is the server's, which is why the
  // confirmation travels with the request instead of dying in the form.
  changePassword(
    newPassword: string,
    confirmPassword: string,
    otp: string,
  ): Promise<void> {
    return apiFetch<void>('user', '/users/me/password', {
      method: 'POST',
      body: JSON.stringify({ newPassword, confirmPassword, otp }),
    })
  },
}
