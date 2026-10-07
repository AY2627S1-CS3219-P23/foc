// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-07, issue #112 (PR #149).
// Scope: the shared body of an emailed-code step — the six boxes, the
// line saying where the code went and how long it lasts, the submit
// button, and the resend link with its cooldown. #92's flows (PR #157)
// gave the profile page three of these (edit account, change password,
// confirm new email), so the layout the sign-up dialog introduced in
// PR #150 lives here instead of being retyped per card.
// Presentational only: the caller owns the digits, runs the calls, and
// wraps this in its own <form> (the button is a plain submit), because
// what a failure means differs per step — see problemTypes.ts.
// Reviewed by: [pending]

import { CodeInput } from './CodeInput'
import { CODE_LENGTH, describeExpiry, useOtpCountdown } from '../otp'

interface CodeStepProps {
  // the address the code was sent to, named so the user knows where to look
  sentTo: string
  // epoch ms; null when the code's remaining life is unknown (a resend
  // refused inside the cooldown tells us one exists, not how long it has)
  expiresAt: number | null
  resendAt: number // epoch ms
  value: string[]
  onChange: (value: string[]) => void
  busy: boolean
  resending: boolean
  submitLabel: string
  busyLabel: string
  onResend: () => void
  onCancel?: () => void
}

export function CodeStep({
  sentTo,
  expiresAt,
  resendAt,
  value,
  onChange,
  busy,
  resending,
  submitLabel,
  busyLabel,
  onResend,
  onCancel,
}: CodeStepProps) {
  const { cooldown, expiresIn } = useOtpCountdown(expiresAt, resendAt)
  const complete = value.every((digit) => digit !== '')

  const resendLabel = resending
    ? 'Sending...'
    : cooldown > 0
      ? `Resend code in ${cooldown}s`
      : 'Resend code'

  return (
    <div className="space-y-4">
      <p className="text-sm text-gray-600">
        Enter the {CODE_LENGTH}-digit code sent to {sentTo}.
      </p>
      <CodeInput
        label="Verification code"
        value={value}
        onChange={onChange}
        disabled={busy || resending}
        autoFocus
      />
      {expiresIn !== null && (
        <p className="text-center text-xs text-gray-500">
          {expiresIn > 0
            ? `The code expires in ${describeExpiry(expiresIn)}.`
            : 'This code has expired — send a new one.'}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="submit"
          // not while a resend is in flight: that call is replacing the
          // code, so submitting the old one would spend an attempt for
          // nothing (PR #150 review)
          disabled={busy || resending || !complete}
          className="flex-1 rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {busy ? busyLabel : submitLabel}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
        )}
      </div>
      <p className="text-center text-sm text-gray-600">
        <button
          type="button"
          onClick={onResend}
          disabled={busy || resending || cooldown > 0}
          className="cursor-pointer font-medium text-gray-900 underline disabled:cursor-default disabled:opacity-50"
        >
          {resendLabel}
        </button>
      </p>
    </div>
  )
}
