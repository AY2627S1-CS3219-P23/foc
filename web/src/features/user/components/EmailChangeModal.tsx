// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-07, issue #112 (PR #149).
// Scope: the second code step #92 requires (F2.1.3) — a new email
// address confirming itself before it replaces the old one. PATCH
// /users/me answers 202 and parks the change; this dialog spends the
// code that went to the NEW address on POST /users/me/email/verify, and
// POST /users/me/email/resend is its own route because the gate code was
// already consumed when the change parked.
// A modal over the profile page rather than a third phase inside the
// Edit card was the author's call via options Q&A (2026-10-07), matching
// the sign-up dialog users already meet; the wireframe draws only one
// code entry, so this step is a documented deviation (web/AGENTS.md).
// Dismissing it keeps the pending change — PR #150's lesson: discarding
// it stranded a code already emailed behind the resend cooldown.
// Reviewed by: [pending]

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { profileApi } from '../profileApi'
import {
  OTP_RESEND_COOLDOWN,
  endsEmailChange,
  problemType,
  retryAfter,
} from '../problemTypes'
import type { AdminUser, PendingEmailChange } from '../types'
import { emptyCode } from '../otp'
import { CodeStep } from './CodeStep'

interface EmailChangeModalProps {
  pending: PendingEmailChange
  onClose: () => void
  onVerified: (user: AdminUser) => void
  // a resend (or a refusal quoting a wait) moved the snapshot's times
  onResent: (pending: PendingEmailChange) => void
  // nothing is left to confirm server-side: drop the snapshot, with the
  // server's sentence to explain why
  onDiscarded: (message: string) => void
}

export function EmailChangeModal({
  pending,
  onClose,
  onVerified,
  onResent,
  onDiscarded,
}: EmailChangeModalProps) {
  const [code, setCode] = useState<string[]>(emptyCode)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const startedAt = (seconds: number) => Date.now() + seconds * 1000

  const verify = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    try {
      onVerified(await profileApi.verifyEmailChange(code.join('')))
    } catch (err: unknown) {
      const message = errorMessage(
        err,
        'Could not confirm the code. Try again.',
      )
      if (endsEmailChange(err)) {
        onDiscarded(message)
        return
      }
      setCode(emptyCode())
      setError(message)
      setBusy(false)
    }
  }

  const resend = async () => {
    setResending(true)
    setError('')
    setNotice('')
    try {
      const accepted = await profileApi.resendEmailChange()
      onResent({
        email: accepted.email,
        expiresAt: startedAt(accepted.expiresInSeconds),
        resendAt: startedAt(accepted.resendInSeconds),
      })
      setCode(emptyCode())
      setNotice('A new code has been sent.')
    } catch (err: unknown) {
      const message = errorMessage(err, 'Could not send a new code. Try again.')
      if (endsEmailChange(err)) {
        onDiscarded(message)
        return
      }
      // the cooldown had not elapsed after all (a stale tab, or clock
      // skew): the header says how much is left
      const wait = retryAfter(err)
      if (problemType(err) === OTP_RESEND_COOLDOWN && wait !== null) {
        onResent({ ...pending, resendAt: startedAt(wait) })
      }
      setError(message)
    } finally {
      setResending(false)
    }
  }

  return (
    <Modal title="Confirm New Email" onClose={onClose}>
      <p className="text-sm text-gray-500">
        Your new address has to confirm itself before it replaces the old one.
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
      <form onSubmit={verify} className="mt-4">
        <CodeStep
          sentTo={pending.email}
          expiresAt={pending.expiresAt}
          resendAt={pending.resendAt}
          value={code}
          onChange={setCode}
          busy={busy}
          resending={resending}
          submitLabel="Verify & Apply"
          busyLabel="Verifying..."
          onResend={() => void resend()}
        />
      </form>
    </Modal>
  )
}
