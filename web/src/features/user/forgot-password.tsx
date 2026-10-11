// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
// Scope: the forgot-password wireframe's two steps on one page
// (web/docs/wireframes/forgot-password.png): email → code + new
// password. POST /auth/password-reset answers an empty 202 whether or
// not the address has an account (owner decision: the page must not
// reveal which), so the step-1 notice is the one neutral sentence and
// only format validation (a 400) shows a real error. Step 2 posts
// email + code + double-entered password to /confirm; every refusal
// of the code is the one reset-code-invalid type — the page keeps the
// fields and offers a resend (which repeats the request; the server
// absorbs repeats inside its cooldown silently). Rewritten the same
// day from a link flow (a /reset-password route) when the owner
// revisited the credential to an OTP code via options Q&A.
// PR #179 Copilot review: "Change email" also clears the typed
// passwords (they silently carried over to the next address), and both
// step-2 escape hatches are disabled while a request is in flight (a
// resend landing after a step switch yanked the user back to the old
// address's code step).
// Author review: Leong Wei Zhi to review via the PR.

import React, { useState } from 'react'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { router } from '../../routes/index'
import { PasswordChecklist } from './PasswordChecklist'
import { RESET_CODE_INVALID, problemType } from './problemTypes'

export function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // a 202 landed for this address: show the code + new-password step.
  // Held as the address it was requested for, so editing the email
  // means starting over rather than confirming against a stale one
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [notice, setNotice] = useState('')

  const requestCode = async (event?: React.SyntheticEvent) => {
    event?.preventDefault()
    setSubmitting(true)
    try {
      await apiFetch<void>('user', '/auth/password-reset', {
        method: 'POST',
        body: JSON.stringify({ email }),
      })
      setError('')
      setSentTo(email)
      // one neutral sentence — also what a resend shows: the server
      // answers 202 either way and absorbs within-cooldown repeats
      setNotice(
        'If an account exists for that email, a reset code is on its way.',
      )
    } catch (error: unknown) {
      // a 400 on the email's format, or a mail 502 — never
      // account-existence, which the server doesn't reveal
      setError(errorMessage(error, 'Could not request a reset. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  const confirmReset = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    if (newPassword !== confirmPassword) {
      setError('Password entries do not match.')
      return
    }
    setSubmitting(true)
    try {
      await apiFetch<void>('user', '/auth/password-reset/confirm', {
        method: 'POST',
        body: JSON.stringify({
          email: sentTo,
          code,
          newPassword,
          confirmPassword,
        }),
      })
      // the login page shows a "password reset" notice (login.tsx)
      router.navigate('/login', { state: { passwordReset: true } })
    } catch (error: unknown) {
      setNotice('')
      setError(
        problemType(error) === RESET_CODE_INVALID
          ? 'That code is invalid or has expired. Check it, or resend a new one.'
          : errorMessage(error, 'Could not reset the password. Try again.'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-2xl font-semibold text-gray-900">
        {sentTo ? 'Create New Password' : 'Reset Your Password'}
      </h2>
      <p className="mt-1 text-sm text-gray-500">
        {sentTo
          ? 'Enter the code we emailed with your new password below.'
          : 'Enter your registered NUS email to receive a reset code.'}
      </p>
      {notice && !error && (
        <p
          role="status"
          className="mt-4 rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          {notice}
        </p>
      )}
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      {!sentTo ? (
        <form onSubmit={requestCode} className="mt-4 space-y-4">
          <label className="block text-sm font-medium text-gray-700">
            NUS Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. e0123456@u.nus.edu"
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
            />
          </label>
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {submitting ? 'Sending...' : 'Send Reset Code'}
          </button>
        </form>
      ) : (
        <form onSubmit={confirmReset} className="mt-4 space-y-4">
          <label className="block text-sm font-medium text-gray-700">
            Reset Code
            <input
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="6-digit code"
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal tracking-widest placeholder:text-gray-400 focus:border-gray-400 focus:outline-none"
            />
          </label>
          <div>
            <label className="block text-sm font-medium text-gray-700">
              New Password
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
              />
            </label>
            <PasswordChecklist password={newPassword} />
          </div>
          <label className="block text-sm font-medium text-gray-700">
            Confirm New Password
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none"
            />
          </label>
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {submitting ? 'Resetting...' : 'Reset Password'}
          </button>
          <p className="text-center text-sm text-gray-600">
            Didn&apos;t get it?{' '}
            <button
              type="button"
              disabled={submitting}
              onClick={() => void requestCode()}
              className="font-medium text-gray-900 underline cursor-pointer disabled:opacity-50"
            >
              Resend code
            </button>{' '}
            ·{' '}
            <button
              type="button"
              disabled={submitting}
              onClick={() => {
                setSentTo(null)
                setCode('')
                // the typed passwords must not silently carry over to
                // whatever address is entered next (PR #179 review)
                setNewPassword('')
                setConfirmPassword('')
                setNotice('')
                setError('')
              }}
              className="font-medium text-gray-900 underline cursor-pointer disabled:opacity-50"
            >
              Change email
            </button>
          </p>
        </form>
      )}
      <p className="mt-4 text-center text-sm text-gray-600">
        <button
          onClick={() => router.navigate('/login')}
          className="font-medium text-gray-900 underline cursor-pointer"
        >
          &larr; Back to Login
        </button>
      </p>
    </div>
  )
}
