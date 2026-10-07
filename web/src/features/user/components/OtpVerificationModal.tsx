// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-09-30, issue #109 (PR #150).
// Scope: the OTP step of sign-up — the overlay in
// docs/wireframes/signup.png. It exists because issue #88 split sign-up in
// two: POST /auth/signup only emails a code (202), and POST
// /auth/signup/verify is what creates the account, so without this screen
// every registration through the site dead-ended at a failed login
// (PR #150 review, @Sinnez1). A modal over the register page rather than
// its own route was Leong Wei Zhi's call via options Q&A: the typed
// credentials stay in the page, which is what lets "Resend code" re-post
// the identical body the server requires.
// PR #150 review (@Sinnez1), three fixes: only the failures that really
// end the pending sign-up send the user back to the form (a 500 or a
// network blip used to, while the code in their inbox was still good);
// Verify is disabled while a resend is in flight, since verifying against
// the code being replaced burns an attempt; and the countdowns are
// derived from absolute times owned by the page, so closing and reopening
// the dialog shows what is really left rather than restarting at 60s.
// The expiry is quoted in minutes rather than counted down to the second —
// the wireframe draws no timer — but it does keep up with the clock, and
// says so plainly once the code has run out.
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): the failures
// that end a pending sign-up are matched by problem+json `type` URI
// (PR #157 added them to the sign-up errors too) instead of by the
// wording of `detail` — the durable fix this file's own comment asked
// for. The two countdowns moved to the shared otpCountdown module, which
// the profile page's code steps use as well; behaviour unchanged.
// Author review: Leong Wei Zhi to review via the PR.

import { useState } from 'react'
import { ApiError, apiFetch, errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { CodeInput } from './CodeInput'
import { CODE_LENGTH, describeExpiry, emptyCode, useOtpCountdown } from '../otp'
import {
  EMAIL_TAKEN,
  OTP_ATTEMPTS_EXCEEDED,
  OTP_EXPIRED,
  USERNAME_TAKEN,
  problemType,
} from '../problemTypes'
import type { SignupAccepted, SignupPending } from '../types'

const CODE_FIELD_LABEL = 'Verification code'

// The verify failures that really end the pending sign-up: after these the
// row is gone server-side (or needs a detail the form owns), so the dialog
// would be lying if it stayed open. Everything else — a 500, a network
// blip, a wrong code — keeps the dialog, because the code in the inbox is
// still good and the user can just try again (PR #150 review).
//
// Matched on the problem+json `type` URIs user-service attaches (#92 /
// PR #157), not on the `detail` sentence this used to compare: a wrong
// code and an unknown email are deliberately given the same answer, so
// the copy was the only signal available before, and copy may be reworded.
const ENDING_TYPES = [
  OTP_EXPIRED,
  OTP_ATTEMPTS_EXCEEDED,
  EMAIL_TAKEN,
  USERNAME_TAKEN,
]

// The one refusal still thrown untyped: AuthService's post-flush race,
// where a concurrent sign-up took the email or username after the checks.
// The remedy is to change a field, and only the form has fields. Giving it
// a type is a one-line user-service follow-up; until then it is matched by
// its sentence, as everything here used to be.
const UNTYPED_ENDING_DETAIL = 'Email or username was just taken; choose another'

function endsTheSignup(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false
  const type = problemType(error)
  if (type) return ENDING_TYPES.includes(type)
  return error.problem?.detail === UNTYPED_ENDING_DETAIL
}

interface OtpVerificationModalProps {
  // the sign-up waiting for a code: the body that was sent, and when the
  // code dies / the next resend is allowed
  pending: SignupPending
  onClose: () => void
  onVerified: () => void
  // a resend (or a refusal quoting a wait) moved those times
  onResent: (pending: SignupPending) => void
  // the pending sign-up is gone server-side: back to the form, with the
  // server's sentence to explain why
  onRestart: (message: string) => void
}

export function OtpVerificationModal({
  pending,
  onClose,
  onVerified,
  onResent,
  onRestart,
}: OtpVerificationModalProps) {
  const [digits, setDigits] = useState<string[]>(emptyCode)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)

  const code = digits.join('')
  const complete = code.length === CODE_LENGTH
  // absolute times owned by the page, so a dialog closed and reopened
  // later shows what is really left rather than restarting at 60s
  const { cooldown, expiresIn } = useOtpCountdown(
    pending.expiresAt,
    pending.resendAt,
  )

  const verify = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await apiFetch<unknown>('user', '/auth/signup/verify', {
        method: 'POST',
        body: JSON.stringify({ email: pending.email, code }),
      })
      onVerified()
    } catch (err: unknown) {
      const message = errorMessage(err, 'Could not verify the code. Try again.')
      if (endsTheSignup(err)) {
        onRestart(message)
        return
      }
      setNotice('')
      setError(message)
      setDigits(emptyCode())
    } finally {
      setBusy(false)
    }
  }

  // A resend is the same POST /auth/signup with the same body — the server
  // treats a repeat of the identical request as the resend, and refuses
  // anything else, which is why the page holds the sent values rather than
  // this dialog reading the form behind it.
  const resend = async () => {
    setResending(true)
    setError('')
    setNotice('')
    try {
      const accepted = await apiFetch<SignupAccepted>('user', '/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          email: pending.email,
          username: pending.username,
          password: pending.password,
        }),
      })
      onResent({
        ...pending,
        expiresAt: Date.now() + accepted.expiresInSeconds * 1000,
        resendAt: Date.now() + accepted.resendInSeconds * 1000,
      })
      setDigits(emptyCode())
      setNotice('A new code has been sent.')
    } catch (err: unknown) {
      // 429 means the cooldown had not elapsed after all (a stale tab, or
      // clock skew): the header says how long is left
      if (err instanceof ApiError && err.retryAfter) {
        onResent({ ...pending, resendAt: Date.now() + err.retryAfter * 1000 })
      }
      setError(errorMessage(err, 'Could not send a new code. Try again.'))
    } finally {
      setResending(false)
    }
  }

  const resendLabel = resending
    ? 'Sending...'
    : cooldown > 0
      ? `Resend code in ${cooldown}s`
      : 'Resend code'

  return (
    <Modal title="OTP Verification" onClose={onClose}>
      <p className="text-sm text-gray-500">
        Enter the {CODE_LENGTH}-digit code sent to {pending.email}.
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
      <form onSubmit={verify} className="mt-4 space-y-4">
        <CodeInput
          label={CODE_FIELD_LABEL}
          value={digits}
          onChange={setDigits}
          disabled={busy || resending}
          autoFocus
        />
        <p className="text-center text-xs text-gray-500">
          {expiresIn
            ? `The code expires in ${describeExpiry(expiresIn)}.`
            : 'This code has expired — send a new one.'}
        </p>
        <button
          type="submit"
          // not while a resend is in flight: that call is replacing the
          // code, so verifying against the old one would spend an attempt
          // for nothing (PR #150 review)
          disabled={busy || resending || !complete}
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {busy ? 'Verifying...' : 'Verify & Activate'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        <button
          type="button"
          onClick={resend}
          disabled={busy || resending || cooldown > 0}
          className="font-medium text-gray-900 underline cursor-pointer disabled:cursor-default disabled:opacity-50"
        >
          {resendLabel}
        </button>
      </p>
    </Modal>
  )
}
