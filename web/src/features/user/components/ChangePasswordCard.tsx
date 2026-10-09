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
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: a password the
// server's own validation refuses (too short, no digit, entries that
// don't match) is an `amend` now, not a code retry — the card returns to
// the fields, which were what it refused, and keeps the unspent code so
// fixing the password costs no resend. Only a wrong code or a failure
// that never judged the password keeps the code step.
// Reviewed by: [pending]

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { PasswordChecklist } from '../PasswordChecklist'
import { profileApi } from '../profileApi'
import { emptyCode, useOtpCountdown } from '../otp'
import {
  OTP_RESEND_COOLDOWN,
  gateFailure,
  problemType,
  retryAfter,
} from '../problemTypes'
import { CodeStep } from './CodeStep'

interface ChangePasswordCardProps {
  // the account's current address, where the gate code goes
  email: string
}

export function ChangePasswordCard({ email }: ChangePasswordCardProps) {
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [code, setCode] = useState<string[]>(emptyCode)
  const [gate, setGate] = useState<{
    expiresAt: number | null
    resendAt: number
  } | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [saved, setSaved] = useState(false)
  const [blockedUntil, setBlockedUntil] = useState(0)
  const { cooldown: blocked } = useOtpCountdown(null, blockedUntil)

  const mismatch = next !== '' && confirm !== '' && next !== confirm

  const startedAt = (seconds: number) => Date.now() + seconds * 1000

  // Same gate code as the Edit Account card: one row per account, so a
  // 429 inside the cooldown means a live code is already in the inbox.
  const sendCode = async (resend: boolean) => {
    if (resend) {
      setResending(true)
    } else {
      setBusy(true)
    }
    setError('')
    setNotice('')
    try {
      const timings = await profileApi.requestOtp()
      setGate({
        expiresAt: startedAt(timings.expiresInSeconds),
        resendAt: startedAt(timings.resendInSeconds),
      })
      setCode(emptyCode())
      setVerifying(true)
      if (resend) setNotice('A new code has been sent.')
    } catch (err: unknown) {
      const wait = retryAfter(err)
      if (problemType(err) === OTP_RESEND_COOLDOWN && wait !== null) {
        setGate({ expiresAt: null, resendAt: startedAt(wait) })
        setVerifying(true)
        setNotice(`A code was sent to ${email} recently — enter it below.`)
        return
      }
      setError(errorMessage(err, 'Could not send the code. Try again.'))
    } finally {
      if (resend) {
        setResending(false)
      } else {
        setBusy(false)
      }
    }
  }

  const update = async () => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await profileApi.changePassword(next, confirm, code.join(''))
      setNext('')
      setConfirm('')
      setCode(emptyCode())
      setGate(null)
      setVerifying(false)
      setSaved(true)
    } catch (err: unknown) {
      switch (gateFailure(err)) {
        case 'retry':
          // the code in the inbox is still good: the step stands, boxes
          // cleared for another attempt
          setCode(emptyCode())
          break
        case 'amend':
          // the password itself was refused, before the code was read —
          // back to the fields holding the unspent code, which submit()
          // reuses rather than spending a resend
          setVerifying(false)
          break
        default: {
          // no usable code any more: the card asks for a new one, after
          // the wait a 429 quoted
          const wait = retryAfter(err)
          setGate(null)
          setVerifying(false)
          setCode(emptyCode())
          if (wait !== null) setBlockedUntil(startedAt(wait))
        }
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
    if (gate) {
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
            expiresAt={gate?.expiresAt ?? null}
            resendAt={gate?.resendAt ?? 0}
            value={code}
            onChange={setCode}
            busy={busy}
            resending={resending}
            submitLabel="Update Password"
            busyLabel="Updating..."
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
            {gate && (
              <p className="text-xs text-gray-500">
                The code already sent to {email} is still valid — continue to
                use it again.
              </p>
            )}
            <button
              type="submit"
              disabled={busy || blocked > 0}
              className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {busy
                ? 'Sending OTP...'
                : blocked > 0
                  ? `Try again in ${blocked}s`
                  : 'Verify & Continue'}
            </button>
          </>
        )}
      </form>
    </section>
  )
}
