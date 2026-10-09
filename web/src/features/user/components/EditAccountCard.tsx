// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: Edit Account Info card of the profile page, per
// web/docs/wireframes/profile.png — desired username/email inputs, then
// an OTP-to-current-email verify step before saving (F2.1-F2.1.3).
// The email helper text says eXXXXXXX@u.nus.edu, following user-service's
// AccountRules regex rather than the wireframe's looser "@u.nus.edu or
// @nus.edu" copy, which the backend does not accept.
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): moved onto
// #92's real endpoints (PR #157). The card-local OtpInput is gone — the
// code step is the shared CodeStep over PR #150's CodeInput — and the
// three answers the real PATCH can give are handled: 200 saves and
// closes, 202 hands a parked email change up to ProfileSection, and a
// refusal is routed by its problem+json type (problemTypes.ts) rather
// than by its wording. A name/address refused as taken rolls the gate
// code's consumption back with it, so the card returns to the fields
// still holding a live code and Save reuses it.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: a retryable
// failure now always leaves the card ON the code step. Reached from the
// fields (where an amended save starts), it used to clear the digits and
// stay there, so the next Save submitted an empty code with no boxes to
// type it into. A body the server's validation refuses is an `amend` too
// now (problemTypes.ts), so the fields it refused stay editable.
// Reviewed by: [pending]

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import { emptyCode, useOtpCountdown } from '../otp'
import {
  OTP_RESEND_COOLDOWN,
  gateFailure,
  problemType,
  retryAfter,
} from '../problemTypes'
import type { AdminUser, EmailChangeAccepted } from '../types'
import { CodeStep } from './CodeStep'

// A gate code the account is holding: when it dies, and when another may
// be requested. expiresAt is null when a cooldown refusal told us a code
// exists without saying how long it has left.
interface Gate {
  expiresAt: number | null
  resendAt: number
}

interface EditAccountCardProps {
  user: AdminUser
  onSaved: (updated: AdminUser) => void
  onEmailPending: (pending: EmailChangeAccepted) => void
  onCancel: () => void
}

export function EditAccountCard({
  user,
  onSaved,
  onEmailPending,
  onCancel,
}: EditAccountCardProps) {
  const [username, setUsername] = useState(user.username)
  const [email, setEmail] = useState(user.email)
  const [code, setCode] = useState<string[]>(emptyCode)
  const [gate, setGate] = useState<Gate | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  // set when the attempt limit was hit: no new code may be requested
  // until the resend cooldown passes, and the 429 quoted the wait
  const [blockedUntil, setBlockedUntil] = useState(0)
  const { cooldown: blocked } = useOtpCountdown(null, blockedUntil)

  const changes = {
    ...(username !== user.username && { username }),
    ...(email !== user.email && { email }),
  }

  const startedAt = (seconds: number) => Date.now() + seconds * 1000

  // Sends (or resends) the gate code. A 429 inside the cooldown is not a
  // dead end: it means a live code is already in the inbox — the other
  // card on this page may have asked for it — so the step opens anyway
  // with the wait it quoted.
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
        setNotice(`A code was sent to ${user.email} recently — enter it below.`)
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

  const save = async () => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const result = await profileApi.updateAccount(changes, code.join(''))
      if (result.kind === 'emailPending') {
        onEmailPending(result.pending)
      } else {
        onSaved(result.user)
      }
    } catch (err: unknown) {
      const message = errorMessage(
        err,
        'Could not save your changes. Try again.',
      )
      switch (gateFailure(err)) {
        case 'restart': {
          const wait = retryAfter(err)
          setGate(null)
          setVerifying(false)
          setCode(emptyCode())
          if (wait !== null) setBlockedUntil(startedAt(wait))
          break
        }
        case 'amend':
          // nothing spent the code refusing these values, so it stays
          // good: back to the fields, Save reuses it
          setVerifying(false)
          break
        default:
          // the code is still live, so the step stands with empty boxes.
          // setVerifying matters when the save was launched FROM the
          // fields after an amend: clearing the digits there left the
          // next Save sending an empty code with nowhere to retype it
          // (PR #149 review)
          setCode(emptyCode())
          setVerifying(true)
      }
      setError(message)
      setBusy(false)
    }
  }

  const submit = (event: React.SyntheticEvent) => {
    event.preventDefault()
    if (verifying) return void save()
    if (!Object.keys(changes).length) {
      setError('Nothing to change.')
      return
    }
    // a code kept from a refused save is still live — don't spend a resend
    if (gate) return void save()
    void sendCode(false)
  }

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">Edit Account Info</h2>
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
      <form onSubmit={submit} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          Desired Username
          <input
            type="text"
            value={username}
            disabled={verifying}
            onChange={(e) => setUsername(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />
        </label>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            NUS Email
            <input
              type="email"
              value={email}
              disabled={verifying}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
            />
          </label>
          <p className="mt-1 text-xs text-gray-500">
            Only eXXXXXXX@u.nus.edu addresses are allowed. A new address
            confirms itself with a second code before it applies.
          </p>
        </div>
        <p className="text-sm text-gray-600">
          To change your email, username, or password, we'll send an OTP to your
          current email for verification.
        </p>
        {verifying ? (
          <CodeStep
            sentTo={user.email}
            expiresAt={gate?.expiresAt ?? null}
            resendAt={gate?.resendAt ?? 0}
            value={code}
            onChange={setCode}
            busy={busy}
            resending={resending}
            submitLabel="Save"
            busyLabel="Saving..."
            onResend={() => void sendCode(true)}
            onCancel={onCancel}
          />
        ) : (
          <div className="space-y-2">
            {gate && (
              <p className="text-xs text-gray-500">
                The code already sent to {user.email} is still valid — Save to
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
                  : gate
                    ? 'Save'
                    : 'Verify & Continue'}
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="w-full rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </button>
          </div>
        )}
      </form>
    </section>
  )
}
