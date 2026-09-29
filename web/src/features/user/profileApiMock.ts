// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: in-memory stand-in for the own-profile endpoints, so the edit
// flows can be walked in the browser before #92 exists. Same signatures
// as profileApi's real calls; used only when VITE_MOCK_PROFILE_API=true
// (see profileApi.ts). The OTP is always 000000 (logged to the console
// on request); the current password is CorrectHorse1; username
// "taken_name" is rejected as taken so the uniqueness error path (F2.1.2)
// can be exercised. Validation mirrors user-service's AccountRules.
//
// TEMPORARY: delete this file (and the switch in profileApi.ts) once
// #92's real endpoints exist.
// Reviewed by: [pending]

import type { AccountChanges } from './profileApi'
import type { AdminUser } from './types'

// AccountRules regexes (user-service dto/AccountRules.java).
const EMAIL_PATTERN = /^\s*e\d{7}@u\.nus\.edu\s*$/i
const USERNAME_PATTERN = /^[a-zA-Z0-9_]{3,30}$/
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{10,50}$/

const MOCK_OTP = '000000'
const MOCK_CURRENT_PASSWORD = 'CorrectHorse1'

// A plain USER, so the details card reads "Requester / Courier".
let me: AdminUser = {
  id: 3,
  email: 'e1000101@u.nus.edu',
  username: 'nus_courier_99',
  role: 'USER',
  createdAt: '2026-09-03T08:00:00Z',
}

// Small delay so loading/busy states are visible during development.
function delay() {
  return new Promise((resolve) => setTimeout(resolve, 150))
}

export const mockProfileApi = {
  async getCurrentUser(): Promise<AdminUser> {
    await delay()
    return { ...me }
  },

  async deleteAccount(): Promise<void> {
    await delay()
  },

  async requestOtp(): Promise<void> {
    await delay()
    console.info(`[mock] OTP for profile change: ${MOCK_OTP}`)
  },

  async updateAccount(changes: AccountChanges, otp: string): Promise<AdminUser> {
    await delay()
    if (otp !== MOCK_OTP) throw new Error('Invalid or expired OTP.')
    if (changes.username !== undefined) {
      if (!USERNAME_PATTERN.test(changes.username))
        throw new Error(
          'Username must be 3-30 alphanumeric characters or underscores.',
        )
      if (changes.username === 'taken_name')
        throw new Error('Username is already taken.')
    }
    if (changes.email !== undefined && !EMAIL_PATTERN.test(changes.email))
      throw new Error('Email must be a valid @u.nus.edu address.')
    me = { ...me, ...changes }
    return { ...me }
  },

  async changePassword(current: string, next: string): Promise<void> {
    await delay()
    if (current !== MOCK_CURRENT_PASSWORD)
      throw new Error('Current password is incorrect.')
    if (!PASSWORD_PATTERN.test(next))
      throw new Error(
        'Password must be 10-50 characters with at least one uppercase letter, one lowercase letter, and one number.',
      )
  },
}
