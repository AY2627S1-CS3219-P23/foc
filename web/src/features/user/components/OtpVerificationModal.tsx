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
// The resend cooldown comes from the 202's resendInSeconds (and a 429's
// Retry-After corrects it); the expiry is stated once rather than counted
// down, as the wireframe draws no timer and the server rejects a stale
// code with a sentence that already says what to do.
// Author review: Leong Wei Zhi to review via the PR.

import { useEffect, useState } from 'react'
import { ApiError, apiFetch, errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { CodeInput } from './CodeInput'
import type { SignupPending } from '../types'

const CODE_LENGTH = 6
const CODE_FIELD_LABEL = 'Verification code'

// The only verify failure the user can act on without starting over.
// user-service makes a wrong code and an unknown email answer identically
// on purpose (see user-service/README.md), and every other failure's
// remedy is literally to sign up again — so the copy is the signal. A
// machine-readable problem "type" would be the durable fix; noted as a
// follow-up rather than invented here.
const WRONG_CODE_DETAIL = 'Invalid verification code'

function describeExpiry(seconds: number): string {
  if (seconds >= 60) {
    const minutes = Math.round(seconds / 60)
    return minutes === 1 ? '1 minute' : `${minutes} minutes`
  }
  return seconds === 1 ? '1 second' : `${seconds} seconds`
}

interface OtpVerificationModalProps {
  // the sign-up that is waiting for a code: the body that was sent, plus
  // what the 202 answered with
  pending: SignupPending
  onClose: () => void
  onVerified: () => void
  // the pending sign-up is gone server-side: back to the form, with the
  // server's sentence to explain why
  onRestart: (message: string) => void
}

export function OtpVerificationModal({
  pending,
  onClose,
  onVerified,
  onRestart,
}: OtpVerificationModalProps) {
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [expiresIn, setExpiresIn] = useState(pending.expiresInSeconds)
  const [cooldown, setCooldown] = useState(pending.resendInSeconds)

  // One pending timeout at a time, re-armed by its own state change — the
  // pattern used elsewhere in web/ (features/user/components/UsersSection)
  // and cleaned up for free, which matters here because the dialog
  // unmounts on close, on success, and twice under StrictMode.
  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((seconds) => seconds - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

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
      const detail = err instanceof ApiError ? err.problem?.detail : undefined
      if (err instanceof ApiError && detail !== WRONG_CODE_DETAIL) {
        // expired, attempts exhausted, or the identifiers were taken: the
        // pending sign-up no longer exists, so staying here would lie
        onRestart(errorMessage(err, 'Could not verify the code. Try again.'))
        return
      }
      setNotice('')
      setError(errorMessage(err, 'Could not verify the code. Try again.'))
      setCode('')
    } finally {
      setBusy(false)
    }
  }

  // A resend is the same POST /auth/signup with the same body — the server
  // treats a repeat of the identical request as the resend, and refuses
  // anything else, which is why `pending` holds the sent values rather
  // than reading the form behind this dialog.
  const resend = async () => {
    setResending(true)
    setError('')
    setNotice('')
    try {
      const accepted = await apiFetch<SignupPending>('user', '/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          email: pending.email,
          username: pending.username,
          password: pending.password,
        }),
      })
      setExpiresIn(accepted.expiresInSeconds)
      setCooldown(accepted.resendInSeconds)
      setCode('')
      setNotice('A new code has been sent.')
    } catch (err: unknown) {
      // 429 means the cooldown had not elapsed after all (a stale tab, or
      // clock skew): the header says how long is left
      if (err instanceof ApiError && err.retryAfter) {
        setCooldown(err.retryAfter)
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
          value={code}
          onChange={setCode}
          length={CODE_LENGTH}
          disabled={busy}
          autoFocus
        />
        <p className="text-center text-xs text-gray-500">
          The code expires in {describeExpiry(expiresIn)}.
        </p>
        <button
          type="submit"
          disabled={busy || code.length < CODE_LENGTH}
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
