// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: Change Password card of the profile page, per
// web/docs/wireframes/profile.png — new/confirm fields and the sign-up
// page's live policy checklist (display-only; the server validates).
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): moved onto
// #92's real POST /users/me/password (PR #157), which takes
// { newPassword, confirmPassword, otp } — there is NO currentPassword,
// because the code emailed to the account's current address is the
// authentication (F2.1.1). The wireframe's Current Password field is
// therefore gone and a code step takes its place: author's call via
// options Q&A, recorded in web/AGENTS.md next to the wireframe it
// deviates from. The confirmation now travels to the server, where the
// double-entry check belongs (F2.1.4); the local match check survives
// only as a pre-flight that saves a round trip.
// 2026-10-10, Claude Code (Opus 5), issue #167: the gate code comes from
// useGateCode (otp.ts) through ProfileSection, so this card and the Edit
// Account card hold the ONE row user-service keeps per account — a code
// either spends or replaces now counts for both — and the request they
// each implemented is one implementation. Local state is this card's
// own: the two password fields, the code step, its messages.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): the kept
// code is only offered while it is alive (its expiry is checked before
// the card says so, and before it reuses it), and a 429's wait counts
// down on the submit button with the code left in place — the Edit
// Account card's two fixes, which this card shares the gate code with.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: a password the
// server's own validation refuses (too short, no digit, entries that
// don't match) is an `amend` now, not a code retry — the card returns to
// the fields, which were what it refused, and keeps the unspent code so
// fixing the password costs no resend. Only a wrong code or a failure
// that never judged the password keeps the code step.
// Author review: Leong Wei Zhi (via PR #149).

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { PasswordChecklist } from '../PasswordChecklist'
import { profileApi } from '../profileApi'
import type { GateCode } from '../otp'
import { CodeStep } from './CodeStep'

interface ChangePasswordCardProps {
  // the account's current address, where the gate code goes
  email: string
  // the page's one gate code, shared with the Edit Account card
  gate: GateCode
}

export function ChangePasswordCard({ email, gate }: ChangePasswordCardProps) {
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [saved, setSaved] = useState(false)

  const mismatch = next !== '' && confirm !== '' && next !== confirm

  // Asks for the gate code (or its resend). One row per account, so a
  // 429 inside the cooldown means a live code is already in the inbox —
  // the Edit Account card may be the one that asked for it.
  const sendCode = async (resend: boolean) => {
    if (resend) {
      setResending(true)
    } else {
      setBusy(true)
    }
    setError('')
    setNotice('')
    const result = await gate.request()
    if (result.kind === 'sent') {
      setVerifying(true)
      if (resend) setNotice('A new code has been sent.')
    } else if (result.kind === 'exists') {
      setVerifying(true)
      setNotice(`A code was sent to ${email} recently — enter it below.`)
    } else {
      setError(
        errorMessage(result.error, 'Could not send the code. Try again.'),
      )
    }
    if (resend) {
      setResending(false)
    } else {
      setBusy(false)
    }
  }

  const update = async () => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await profileApi.changePassword(next, confirm, gate.digits.join(''))
      gate.spent()
      setNext('')
      setConfirm('')
      setVerifying(false)
      setSaved(true)
    } catch (err: unknown) {
      // the shared code has already been moved by the failure; this card
      // moves its own step
      switch (gate.failed(err)) {
        case 'wait':
        case 'retry':
          // the code is still the live one: the step stands, with its
          // digits (a wait) or cleared for another attempt (a retry)
          break
        default:
          // a new code is needed, or the password was what was refused:
          // either way the fields are where the user goes
          setVerifying(false)
      }
      setError(errorMessage(err, 'Could not update your password. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  const submit = (event: React.SyntheticEvent) => {
    event.preventDefault()
    setSaved(false)
    if (verifying) return void update()
    if (next !== confirm) {
      setError('New passwords do not match.')
      return
    }
    // a refusal that never reached the gate — a password the policy
    // rejects, say — leaves the code unconsumed, so going back to the
    // step must not spend a resend on a code already in the inbox
    if (gate.live) {
      setVerifying(true)
      setError('')
      return
    }
    void sendCode(false)
  }

  const inputClass =
    'mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500'

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">Change Password</h2>
      <p className="mt-1 text-sm text-gray-500">
        Choose a strong 10-50 character password. We'll send a code to {email}{' '}
        to confirm it's you.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      {notice && (
        <p
          role="status"
          className="mt-4 rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          {notice}
        </p>
      )}
      {saved && (
        <p
          role="status"
          className="mt-4 rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          Password updated.
        </p>
      )}
      <form onSubmit={submit} className="mt-4 space-y-4">
        {/* Hidden, but present: a password form without a username field
            gives password managers nothing to attach the new password to
            (Chrome warns about it), and this card has no visible one. */}
        <input
          type="text"
          name="username"
          autoComplete="username"
          value={email}
          readOnly
          hidden
        />
        <label className="block text-sm font-medium text-gray-700">
          New Password
          <input
            type="password"
            autoComplete="new-password"
            required
            disabled={verifying}
            value={next}
            onChange={(e) => setNext(e.target.value)}
            className={inputClass}
          />
        </label>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            Confirm New Password
            <input
              type="password"
              autoComplete="new-password"
              required
              disabled={verifying}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className={inputClass}
            />
          </label>
          <p
            className={`mt-1 text-xs ${mismatch ? 'text-red-600' : 'text-gray-500'}`}
          >
            Passwords must match
          </p>
          <PasswordChecklist password={next} />
        </div>
        {verifying ? (
          <CodeStep
            sentTo={email}
            expiresAt={gate.expiresAt}
            resendAt={gate.resendAt}
            value={gate.digits}
            onChange={gate.setDigits}
            busy={busy}
            resending={resending}
            submitLabel="Update Password"
            busyLabel="Updating..."
            blockedFor={gate.blocked}
            onResend={() => void sendCode(true)}
            onCancel={() => {
              setVerifying(false)
              setError('')
              setNotice('')
            }}
          />
        ) : (
          <>
            {/* a code kept from a refused password is still live: say so,
                as the Edit Account card does, so "Verify & Continue"
                isn't read as spending a resend */}
            {gate.live && (
              <p className="text-xs text-gray-500">
                The code already sent to {email} is still valid — continue to
                use it again.
              </p>
            )}
            <button
              type="submit"
              disabled={busy || gate.blocked > 0}
              className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {busy
                ? 'Sending OTP...'
                : gate.blocked > 0
                  ? `Try again in ${gate.blocked}s`
                  : 'Verify & Continue'}
            </button>
          </>
        )}
      </form>
    </section>
  )
}
