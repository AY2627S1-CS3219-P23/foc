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
// 2026-10-10, Claude Code (Opus 5), issue #167: the gate code is no
// longer this card's. useGateCode (otp.ts) owns the account's one row
// and the digits typed for it, ProfileSection runs it once, and this
// card receives it — so a code the Change Password card spends or
// replaces is spent or replaced here too, and the request both cards
// used to implement separately is one implementation. What stays local
// is this card's own: its fields, whether the code step is showing, and
// its error and notice lines.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): two
// things about the code the card keeps across a refusal. A 429's wait is
// counted down on the submit button and the code left in place (`wait`,
// problemTypes.ts) instead of being read as a wrong guess — a PATCH that
// parks an email change inside a previous change's cooldown is refused
// that way, and clearing the boxes had the user retyping a good code
// into a call that could only fail again. And a kept code is only
// offered while it is still alive: once its own expiry passes, Save asks
// for a new one rather than sending a code the server will refuse.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: a retryable
// failure now always leaves the card ON the code step. Reached from the
// fields (where an amended save starts), it used to clear the digits and
// stay there, so the next Save submitted an empty code with no boxes to
// type it into. A body the server's validation refuses is an `amend` too
// now (problemTypes.ts), so the fields it refused stay editable.
// Author review: Leong Wei Zhi (via PR #149).

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { GateCode } from '../otp'
import type { AdminUser, EmailChangeAccepted } from '../types'
import { CodeStep } from './CodeStep'

interface EditAccountCardProps {
  user: AdminUser
  // the page's one gate code, shared with the Change Password card
  gate: GateCode
  onSaved: (updated: AdminUser) => void
  onEmailPending: (pending: EmailChangeAccepted) => void
  onCancel: () => void
}

export function EditAccountCard({
  user,
  gate,
  onSaved,
  onEmailPending,
  onCancel,
}: EditAccountCardProps) {
  const [username, setUsername] = useState(user.username)
  const [email, setEmail] = useState(user.email)
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const changes = {
    ...(username !== user.username && { username }),
    ...(email !== user.email && { email }),
  }

  // Asks for the gate code (or its resend). A 429 inside the cooldown is
  // not a dead end: a live code is already in the inbox — the other card
  // may have asked for it — so the step opens anyway, with the wait the
  // server quoted.
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
      setNotice(`A code was sent to ${user.email} recently — enter it below.`)
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

  const save = async () => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const result = await profileApi.updateAccount(
        changes,
        gate.digits.join(''),
      )
      // single-use: the row is gone server-side, for this card and the
      // other one (a 202's parked email change spent it too)
      gate.spent()
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
      // the shared code has already been moved by the failure; this card
      // moves its own step
      switch (gate.failed(err)) {
        case 'wait':
          // the step stands with its digits: only the clock is in the way
          break
        case 'restart':
        case 'amend':
          // either a new code is needed, or the fields were what was
          // refused — both send the user back to them
          setVerifying(false)
          break
        default:
          // the code is still live, so the step stands with empty boxes.
          // This matters most when the save was launched FROM the fields
          // after an amend: the cleared digits would otherwise leave the
          // next Save sending an empty code with nowhere to retype it
          // (PR #149 review)
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
    if (gate.live) return void save()
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
            expiresAt={gate.expiresAt}
            resendAt={gate.resendAt}
            value={gate.digits}
            onChange={gate.setDigits}
            busy={busy}
            resending={resending}
            submitLabel="Save"
            busyLabel="Saving..."
            blockedFor={gate.blocked}
            onResend={() => void sendCode(true)}
            onCancel={onCancel}
          />
        ) : (
          <div className="space-y-2">
            {gate.live && (
              <p className="text-xs text-gray-500">
                The code already sent to {user.email} is still valid — Save to
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
                  : gate.live
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
