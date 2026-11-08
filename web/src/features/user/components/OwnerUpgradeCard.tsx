// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-08, issue #308.
// Scope: the profile page's owner upgrade card — the owner setup token
// plus the page's shared gate code (useGateCode), posted to
// POST /users/me/owner. Shape follows ChangePasswordCard. Decisions by
// Leong Wei Zhi via options Q&A (2026-11-08): the gate code is required,
// a wrong token keeps the code (the card goes back to its field, code
// held), and success signs the user out with a notice — every session of
// the account is revoked server-side. Not drawn in profile.png; see
// web/AGENTS.md.
// Author review: Leong Wei Zhi (via the PR).

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { GateCode } from '../otp'
import { CodeStep } from './CodeStep'

interface OwnerUpgradeCardProps {
  // the account's current address, where the gate code goes
  email: string
  // the page's one gate code, shared with the other cards
  gate: GateCode
  // the account is OWNER now and its sessions are revoked
  onUpgraded: () => void
}

export function OwnerUpgradeCard({
  email,
  gate,
  onUpgraded,
}: OwnerUpgradeCardProps) {
  const [ownerKey, setOwnerKey] = useState('')
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // ChangePasswordCard's request: one row per account, so a 429 inside
  // the cooldown means a live code is already in the inbox
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

  const upgrade = async () => {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await profileApi.upgradeToOwner(ownerKey, gate.digits.join(''))
      gate.spent()
      onUpgraded()
    } catch (err: unknown) {
      switch (gate.failed(err)) {
        case 'wait':
        case 'retry':
          break
        default:
          // a new code is needed, or the token was refused (its code
          // kept): either way the token field is where the user goes
          setVerifying(false)
      }
      setError(errorMessage(err, 'Could not upgrade your account. Try again.'))
      setBusy(false)
    }
  }

  const submit = (event: React.SyntheticEvent) => {
    event.preventDefault()
    if (verifying) return void upgrade()
    // a refused token leaves the code unconsumed: reuse it
    if (gate.live) {
      setVerifying(true)
      setError('')
      return
    }
    void sendCode(false)
  }

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">Become an Owner</h2>
      <p className="mt-1 text-sm text-gray-500">
        Enter the owner setup token to make this account an owner. We'll send a
        code to {email} to confirm it's you, and you'll sign in again
        afterwards.
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
      <form onSubmit={submit} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          Owner Setup Token
          <input
            type="password"
            autoComplete="off"
            required
            disabled={verifying}
            value={ownerKey}
            onChange={(e) => setOwnerKey(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />
        </label>
        {verifying ? (
          <CodeStep
            sentTo={email}
            expiresAt={gate.expiresAt}
            resendAt={gate.resendAt}
            value={gate.digits}
            onChange={gate.setDigits}
            busy={busy}
            resending={resending}
            submitLabel="Become Owner"
            busyLabel="Upgrading..."
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
            {gate.live && (
              <p className="text-xs text-gray-500">
                {gate.life === null
                  ? `A code was already sent to ${email} — `
                  : `The code already sent to ${email} is still valid — `}
                continue to use it again.
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
