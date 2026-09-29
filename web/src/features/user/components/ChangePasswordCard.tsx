// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: Change Password card of the profile page, per
// web/docs/wireframes/profile.png — current/new/confirm fields, the
// sign-up page's live policy checklist (display-only; the server
// validates), and a client-side match check (the server never sees the
// confirm field). Calls profileApi's PROVISIONAL #92 contract.
// Reviewed by: [pending]

import React, { useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { PasswordChecklist } from '../PasswordChecklist'
import { profileApi } from '../profileApi'

export function ChangePasswordCard() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  const mismatch = next !== '' && confirm !== '' && next !== confirm

  const submit = async (event: React.SyntheticEvent) => {
    event.preventDefault()
    setSaved(false)
    if (next !== confirm) {
      setError('New passwords do not match.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await profileApi.changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      setSaved(true)
    } catch (err: unknown) {
      setError(errorMessage(err, 'Could not update your password. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  const inputClass =
    'mt-1 w-full rounded-md border border-gray-200 px-3 py-2 font-normal focus:border-gray-400 focus:outline-none'

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">Change Password</h2>
      <p className="mt-1 text-sm text-gray-500">
        Choose a strong 10-50 character password.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      {saved && (
        <p
          role="status"
          className="mt-4 rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          Password updated.
        </p>
      )}
      <form onSubmit={submit} className="mt-4 space-y-4">
        <label className="block text-sm font-medium text-gray-700">
          Current Password
          <input
            type="password"
            autoComplete="current-password"
            required
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            className={inputClass}
          />
        </label>
        <label className="block text-sm font-medium text-gray-700">
          New Password
          <input
            type="password"
            autoComplete="new-password"
            required
            value={next}
            onChange={(e) => setNext(e.target.value)}
            className={inputClass}
          />
        </label>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            Confirm New Password
            <input
              type="password"
              autoComplete="new-password"
              required
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className={inputClass}
            />
          </label>
          <p
            className={`mt-1 text-xs ${mismatch ? 'text-red-600' : 'text-gray-500'}`}
          >
            Passwords must match
          </p>
          <PasswordChecklist password={next} />
        </div>
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {busy ? 'Updating...' : 'Update Password'}
        </button>
      </form>
    </section>
  )
}
