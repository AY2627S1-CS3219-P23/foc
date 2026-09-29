// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: Edit Account Info card of the profile page, per
// web/docs/wireframes/profile.png — desired username/email inputs, then
// an OTP-to-current-email verify step before saving (F2.1-F2.1.3).
// Calls profileApi's PROVISIONAL #92 contract. The email helper text
// says eXXXXXXX@u.nus.edu, following user-service's AccountRules regex
// rather than the wireframe's looser "@u.nus.edu or @nus.edu" copy,
// which the backend does not accept.
// Reviewed by: [pending]

import React, { useRef, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { AdminUser } from '../types'

const OTP_LENGTH = 6

// Six single-digit boxes; auto-advance on input, backspace steps back,
// paste spreads its digits. One entry per box ('' when empty).
function OtpInput({
  value,
  onChange,
}: {
  value: string[]
  onChange: (value: string[]) => void
}) {
  const refs = useRef<(HTMLInputElement | null)[]>([])

  const focusBox = (i: number) => {
    refs.current[Math.min(i, OTP_LENGTH - 1)]?.focus()
  }

  return (
    <div className="flex justify-center gap-2">
      {value.map((digit, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el
          }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          aria-label={`OTP digit ${i + 1}`}
          value={digit}
          onChange={(e) => {
            const entered = e.target.value.replace(/\D/g, '').slice(-1)
            onChange(value.map((d, j) => (j === i ? entered : d)))
            if (entered) focusBox(i + 1)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace' && !digit && i > 0) focusBox(i - 1)
          }}
          onPaste={(e) => {
            e.preventDefault()
            const pasted = e.clipboardData.getData('text').replace(/\D/g, '')
            if (!pasted) return
            onChange(
              value.map((d, j) => (j < pasted.length ? pasted[j] : d)),
            )
            focusBox(pasted.length)
          }}
          className="h-10 w-10 rounded-md border border-gray-200 text-center text-sm focus:border-gray-400 focus:outline-none"
        />
      ))}
    </div>
  )
}

interface EditAccountCardProps {
  user: AdminUser
  onSaved: (updated: AdminUser) => void
  onCancel: () => void
}

export function EditAccountCard({
  user,
  onSaved,
  onCancel,
}: EditAccountCardProps) {
  const [username, setUsername] = useState(user.username)
  const [email, setEmail] = useState(user.email)
  const [otp, setOtp] = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const [verifying, setVerifying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const changes = {
    ...(username !== user.username && { username }),
    ...(email !== user.email && { email }),
  }

  const requestOtp = async () => {
    if (!Object.keys(changes).length) {
      setError('Nothing to change.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await profileApi.requestOtp()
      setVerifying(true)
    } catch (err: unknown) {
      setError(errorMessage(err, 'Could not send the OTP. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  const save = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      onSaved(await profileApi.updateAccount(changes, otp.join('')))
    } catch (err: unknown) {
      setError(errorMessage(err, 'Could not save your changes. Try again.'))
      setBusy(false)
    }
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
      <form
        onSubmit={verifying ? save : (e) => e.preventDefault()}
        className="mt-4 space-y-4"
      >
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
            Only eXXXXXXX@u.nus.edu addresses are allowed.
          </p>
        </div>
        <p className="text-sm text-gray-600">
          To change your email, username, or password, we'll send an OTP to
          your current email for verification.
        </p>
        {verifying ? (
          <>
            <OtpInput value={otp} onChange={setOtp} />
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={busy || otp.some((digit) => !digit)}
                className="flex-1 rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
              >
                {busy ? 'Saving...' : 'Save'}
              </button>
              <button
                type="button"
                onClick={onCancel}
                className="flex-1 rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </>
        ) : (
          <div className="space-y-2">
            <button
              type="button"
              onClick={requestOtp}
              disabled={busy}
              className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {busy ? 'Sending OTP...' : 'Verify & Continue'}
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
