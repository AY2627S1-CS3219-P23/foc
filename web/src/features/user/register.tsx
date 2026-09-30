// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issues #87/#89.
// Scope: registration now calls user-service's POST /auth/signup (replacing
// PR #139's user-auth server on port 8081) with the NUS email it
// requires, then sends you to log in (sign-up returns the account, not
// a token); shows the server's error message. The response (the new
// account) is not logged (PR #141 review). Second PR #141 review: calls go
// through apiFetch, so a missing VITE_USER_SERVICE_URL shows as such; no
// success log. The page itself comes from PR #139.
// 2026-09-29, Claude Code (Fable 5): restyled to the sign-up wireframe
// (web/docs/wireframes/signup.png) — centered card, stacked labels, error
// alert box, live password checklist mirroring user-service's AccountRules
// (display-only; the server still validates) — logic otherwise unchanged.
// PR #142 Copilot review: role="alert" on the error message; checklist
// moved outside the password <label> (a ul is not phrasing content and
// was polluting the field's accessible name).
// 2026-09-29, Claude Code (Opus 5.5), issue #147: errorMessage now comes
// from lib/api/http; Prettier formatting.
// 2026-09-29 (issue #147): the login page is told the account was created.
// 2026-09-30, Claude Code (Opus 5), issue #109 (PR #150): sign-up no longer
// ends here. POST /auth/signup answers 202 and only emails a code (issue
// #88), so this page opens the OTP dialog and the account is created by the
// verify call inside it — before this, every registration through the site
// dead-ended at a failed login (PR #150 review, @Sinnez1). The values that
// were sent are snapshotted, because user-service treats only a repeat of
// the identical request as the resend. Modal over this page rather than a
// second route: Leong Wei Zhi's call via options Q&A.
// Reviewed by: Ryan Ang

import React, { useState } from 'react'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { router } from '../../routes/index'
import { OtpVerificationModal } from './components/OtpVerificationModal'
import type { SignupPending } from './types'

// Mirrors user-service's AccountRules password policy (PASSWORD_PATTERN,
// PASSWORD_MIN/MAX). Display-only: the server remains the validator.
const passwordRules = [
  {
    label: 'Contains uppercase and lowercase',
    met: (p: string) => /[a-z]/.test(p) && /[A-Z]/.test(p),
  },
  {
    label: 'Contains numbers',
    met: (p: string) => /\d/.test(p),
  },
  {
    label: 'Length between 10 and 50 characters',
    met: (p: string) => p.length >= 10 && p.length <= 50,
  },
]

function PasswordChecklist({ password }: { password: string }) {
  return (
    <ul className="mt-2 space-y-0.5 text-xs font-normal">
      {passwordRules.map(({ label, met }) => {
        const ok = met(password)
        return (
          <li key={label} className={ok ? 'text-green-600' : 'text-red-600'}>
            {ok ? '✓' : '✗'} {label}
          </li>
        )
      })}
    </ul>
  )
}

export function Register() {
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // set once a code is on its way: the body that was sent, plus what the
  // 202 answered. A snapshot rather than a read of the live fields, so a
  // resend repeats the identical request even if the form is edited behind
  // the dialog — anything else would be refused with a 409
  const [pending, setPending] = useState<SignupPending | null>(null)

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const accepted = await apiFetch<SignupPending>('user', '/auth/signup', {
        method: 'POST',
        body: JSON.stringify({ email, username, password }),
      })

      setError('')
      // no account exists yet, and nothing is cleared: the dialog takes
      // over from here, and its verify call is what creates the account
      setPending({ ...accepted, email, username, password })
    } catch (error: unknown) {
      setError(errorMessage(error, 'Could not register. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  // the account exists now, so finish where sign-up always finished
  const accountCreated = () => {
    setPending(null)
    setEmail('')
    setUsername('')
    setPassword('')
    // the login page shows an "account created" notice (issue #147)
    router.navigate('/login', { state: { accountCreated: true } })
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-2xl font-semibold text-gray-900">Create Account</h2>
      <p className="mt-1 text-sm text-gray-500">
        Join NUS peer-to-peer errand network.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      <form onSubmit={register} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          NUS Email Address
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e1234567@u.nus.edu"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
          />
        </label>
        <label className="block text-sm font-medium text-gray-700">
          Username
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
          />
        </label>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
            />
          </label>
          <PasswordChecklist password={password} />
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {submitting ? 'Sending code...' : 'Sign Up'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        Already have an account?{' '}
        <button
          onClick={() => router.navigate('/login')}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          Log in
        </button>
      </p>
      {/* outside the <form>: the dialog holds a form of its own, and forms
          may not nest */}
      {pending && (
        <OtpVerificationModal
          pending={pending}
          onClose={() => setPending(null)}
          onVerified={accountCreated}
          onRestart={(message) => {
            // the pending sign-up is dead (expired, or too many wrong
            // codes): back to the still-filled form, one click from a
            // fresh code
            setPending(null)
            setError(message)
          }}
        />
      )}
    </div>
  )
}
