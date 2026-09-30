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
// 2026-09-29, Claude Code (Fable 5), issue #112: errorMessage and the
// password checklist moved out to shared homes (lib/api/http.ts and
// PasswordChecklist.tsx) for reuse by the profile page; logic unchanged.
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
// PR #150 review (@Sinnez1): dismissing the dialog keeps the pending
// sign-up and offers "Enter your code" to reopen it — discarding it
// stranded the code already emailed behind the resend cooldown. The
// snapshot holds absolute times so a reopened dialog counts from the
// truth.
// Reviewed by: Ryan Ang

import React, { useState } from 'react'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { router } from '../../routes/index'
import { OtpVerificationModal } from './components/OtpVerificationModal'
import type { SignupAccepted, SignupPending } from './types'
import { PasswordChecklist } from "./PasswordChecklist";

export function Register() {
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // set once a code is on its way: the body that was sent, plus when that
  // code dies and when it may be resent. A snapshot rather than a read of
  // the live fields, so a resend repeats the identical request even if the
  // form is edited behind the dialog — anything else would be refused 409
  const [pending, setPending] = useState<SignupPending | null>(null)
  // dismissing the dialog hides it but keeps the pending sign-up, so the
  // code already emailed can still be entered — closing it used to strand
  // that code behind a cooldown and a pointless resend (PR #150 review)
  const [codeOpen, setCodeOpen] = useState(false)

  const register = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const accepted = await apiFetch<SignupAccepted>('user', '/auth/signup', {
        method: 'POST',
        body: JSON.stringify({ email, username, password }),
      })

      setError('')
      // no account exists yet, and nothing is cleared: the dialog takes
      // over from here, and its verify call is what creates the account
      setPending({
        email,
        username,
        password,
        expiresAt: Date.now() + accepted.expiresInSeconds * 1000,
        resendAt: Date.now() + accepted.resendInSeconds * 1000,
      })
      setCodeOpen(true)
    } catch (error: unknown) {
      setError(errorMessage(error, 'Could not register. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  // the account exists now, so finish where sign-up always finished
  const accountCreated = () => {
    setPending(null)
    setCodeOpen(false)
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
      {pending && !codeOpen && (
        <p className="mt-4 text-center text-sm text-gray-600">
          We emailed a code to {pending.email}.{' '}
          <button
            type="button"
            onClick={() => setCodeOpen(true)}
            className="font-medium text-gray-900 underline cursor-pointer"
          >
            Enter your code
          </button>
        </p>
      )}
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
      {pending && codeOpen && (
        <OtpVerificationModal
          pending={pending}
          // hidden, not discarded: "Enter your code" brings it back
          onClose={() => setCodeOpen(false)}
          onVerified={accountCreated}
          onResent={setPending}
          onRestart={(message) => {
            // the pending sign-up is dead (expired, or too many wrong
            // codes): back to the still-filled form, one click from a
            // fresh code
            setPending(null)
            setCodeOpen(false)
            setError(message)
          }}
        />
      )}
    </div>
  )
}
