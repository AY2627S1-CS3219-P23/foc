// AI-assisted (CS3219 AI Usage Policy disclosure):
// Copied from registerOwner.tsx which was AI-assisted

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
import type { OwnerSignupPending, SignupAccepted } from '../types'

const CODE_FIELD_LABEL = 'Verification code'

// The failures that end the pending sign-up
// Wrong owner code does not end sign-upto prevent brute-force guessing
const ENDING_TYPES = [
  OTP_EXPIRED,
  OTP_ATTEMPTS_EXCEEDED,
  EMAIL_TAKEN,
  USERNAME_TAKEN,
]

// The one refusal thrown untyped, ie `about:blank`
const UNTYPED_ENDING_DETAIL = 'Email or username was just taken; choose another'

function endsTheSignup(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false
  const type = problemType(error)
  if (type) return ENDING_TYPES.includes(type)
  // Untyped refusals: 429 means no attempts left
  return error.status === 429 || error.problem?.detail === UNTYPED_ENDING_DETAIL
}

interface OtpVerificationModalProps {
  // the sign-up waiting for a code: the body that was sent, and when the
  // code dies / the next resend is allowed
  pending: OwnerSignupPending
  onClose: () => void
  onVerified: () => void
  onResent: (pending: OwnerSignupPending) => void
  onRestart: (message: string) => void
}

export function OwnerOtpVerificationModal({
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
      await apiFetch<unknown>('user', '/auth/signup/verify-owner', {
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
      const accepted = await apiFetch<SignupAccepted>('user', '/auth/signup-owner', {
        method: 'POST',
        body: JSON.stringify({
          email: pending.email,
          username: pending.username,
          password: pending.password,
          ownerKey: pending.ownerKey
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
