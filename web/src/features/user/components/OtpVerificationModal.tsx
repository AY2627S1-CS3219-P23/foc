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
// Author review: Leong Wei Zhi to review via the PR.

import { useEffect, useState } from 'react'
import { ApiError, apiFetch, errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { CodeInput } from './CodeInput'
import type { SignupAccepted, SignupPending } from '../types'

const CODE_LENGTH = 6
const CODE_FIELD_LABEL = 'Verification code'

const emptyCode = () => Array<string>(CODE_LENGTH).fill('')

// The verify failures that really end the pending sign-up: after these the
// row is gone server-side (or needs a detail the form owns), so the dialog
// would be lying if it stayed open. Everything else — a 500, a network
// blip, a wrong code — keeps the dialog, because the code in the inbox is
// still good and the user can just try again (PR #150 review).
//
// Keyed on the sentences user-service/README.md documents, because a wrong
// code and an unknown email are deliberately given the same answer there,
// so the copy is the only signal. A machine-readable "type" in the
// problem+json body is the durable fix; noted as a follow-up.
const ENDS_THE_SIGNUP = [
  'Code has expired; sign up again to get a new code',
  'Email is already registered',
  'Username is already taken',
  // the row survives this one (its transaction rolls back), but the
  // remedy is to change a field, and only the form has fields
  'Email or username was just taken; choose another',
]

function endsTheSignup(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false
  // 429 on verify is the attempt limit, which discards the sign-up
  return (
    error.status === 429 ||
    ENDS_THE_SIGNUP.includes(error.problem?.detail ?? '')
  )
}

function describeExpiry(seconds: number): string {
  if (seconds >= 60) {
    const minutes = Math.round(seconds / 60)
    return minutes === 1 ? '1 minute' : `${minutes} minutes`
  }
  return seconds === 1 ? '1 second' : `${seconds} seconds`
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
  const [now, setNow] = useState(() => Date.now())

  const code = digits.join('')
  const complete = code.length === CODE_LENGTH
  const cooldown = Math.max(0, Math.ceil((pending.resendAt - now) / 1000))
  const expiresIn = Math.max(0, Math.ceil((pending.expiresAt - now) / 1000))

  // One pending timeout at a time, re-armed by its own state change — the
  // pattern used elsewhere in web/ (features/user/components/UsersSection)
  // and cleaned up for free, which matters here because the dialog
  // unmounts on close, on success, and twice under StrictMode.
  //
  // It runs while either line is still moving. Keying it on the cooldown
  // alone froze the expiry sentence the moment the cooldown ran out
  // (PR #150 review) — the countdown stalled at whatever it read, usually
  // "9 minutes", until the dialog was reopened. The cooldown needs
  // per-second precision; the expiry is quoted in minutes, so on its own
  // it doesn't deserve a ticking second hand.
  useEffect(() => {
    if (cooldown <= 0 && expiresIn <= 0) return
    const timer = setTimeout(
      () => setNow(Date.now()),
      cooldown > 0 ? 1000 : 15000,
    )
    return () => clearTimeout(timer)
  }, [cooldown, expiresIn])

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
      setNow(Date.now())
      setNotice('A new code has been sent.')
    } catch (err: unknown) {
      // 429 means the cooldown had not elapsed after all (a stale tab, or
      // clock skew): the header says how long is left
      if (err instanceof ApiError && err.retryAfter) {
        onResent({ ...pending, resendAt: Date.now() + err.retryAfter * 1000 })
        setNow(Date.now())
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
          {expiresIn > 0
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
