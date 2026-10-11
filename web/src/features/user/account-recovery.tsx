// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
// Scope: the account-recovery wireframe
// (web/docs/wireframes/account-recovery.png): a deleted account just
// logged in (login reports the purge date instead of silently
// recovering — owner decision), and the caller chooses. "Restore My
// Account" calls POST /users/me/recover and hands the returned account
// to restored(), which also strips the session's deletion flag; "Keep
// Deleted" is simply logout() — the deletion proceeds on its own, no
// server call needed. ProtectedRoute holds a flagged session on this
// page, and an unflagged visitor is sent home.
// Author review: Leong Wei Zhi to review via the PR.

import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { apiFetch, errorMessage } from '@/lib/api/http'
import { useAuth } from './useAuth'
import type { AdminUser } from './types'

export function AccountRecovery() {
  const { token, restored, logout } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [restoring, setRestoring] = useState(false)

  // nothing to recover: an active session has no business here
  if (!token?.deletionScheduledFor) {
    return <Navigate to="/" replace />
  }

  const purgeDate = new Date(token.deletionScheduledFor).toLocaleDateString(
    undefined,
    { year: 'numeric', month: 'short', day: 'numeric' },
  )

  const restore = async () => {
    setRestoring(true)
    try {
      const user = await apiFetch<AdminUser>('user', '/users/me/recover', {
        method: 'POST',
      })
      restored(user)
      navigate('/', { replace: true })
    } catch (error: unknown) {
      // e.g. the window lapsed between login and this click (404)
      setError(errorMessage(error, 'Could not restore the account. Try again.'))
      setRestoring(false)
    }
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-md rounded-lg border border-gray-200 bg-white p-8 text-center shadow-sm">
      <div
        aria-hidden="true"
        className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 text-xl font-semibold text-gray-700"
      >
        !
      </div>
      <h2 className="mt-4 text-2xl font-semibold text-gray-900">
        Account Scheduled for Deletion
      </h2>
      <p className="mt-2 text-sm text-gray-600">
        Your account is scheduled for permanent deletion on{' '}
        <span className="font-semibold text-gray-900">{purgeDate}</span>. You
        can restore it now or let the deletion proceed.
      </p>
      {error && (
        <p
          role="alert"
          className="mt-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      <div className="mt-6 flex justify-center gap-3">
        <button
          onClick={restore}
          disabled={restoring}
          className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {restoring ? 'Restoring...' : 'Restore My Account'}
        </button>
        <button
          onClick={logout}
          disabled={restoring}
          className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Keep Deleted
        </button>
      </div>
      <p className="mt-4 text-xs text-gray-500">
        Once restored, your credits and history will be fully accessible again.
      </p>
    </div>
  )
}
