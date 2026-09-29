// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: the profile page's card stack, per
// web/docs/wireframes/profile.png (the mobile column's layout at every
// size; the desktop My Settings sidebar was dropped — it has no second
// page to link yet). Loads GET /users/me, coordinates the edit card,
// and runs the delete flow: DELETE /users/me then logout(), which
// clears the stored session and navigates home.
// Reviewed by: [pending]

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { AdminUser } from '../types'
import { useAuth } from '../useAuth'
import { ChangePasswordCard } from './ChangePasswordCard'
import { DeleteAccountModal } from './DeleteAccountModal'
import { EditAccountCard } from './EditAccountCard'
import { ProfileDetailsCard } from './ProfileDetailsCard'

export function ProfileSection() {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loadError, setLoadError] = useState('')
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const { logout } = useAuth()

  useEffect(() => {
    let cancelled = false
    profileApi.getCurrentUser().then(
      (me) => {
        if (!cancelled) setUser(me)
      },
      (err: unknown) => {
        if (!cancelled)
          setLoadError(errorMessage(err, 'Could not load your profile.'))
      },
    )
    return () => {
      cancelled = true
    }
  }, [])

  const deleteAccount = async () => {
    setDeleting(true)
    setDeleteError('')
    try {
      await profileApi.deleteAccount()
      // Clears the stored session. logout() aims for the home page, but
      // ProtectedRoute's /login redirect wins from any protected page
      // (the token-clearing render outruns the navigation transition) —
      // the same landing every logout in the app gets from here.
      logout()
    } catch (err: unknown) {
      setDeleteError(
        errorMessage(err, 'Could not delete your account. Try again.'),
      )
      setDeleting(false)
    }
  }

  if (loadError) {
    return (
      <p
        role="alert"
        className="mx-auto max-w-md rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
      >
        {loadError}
      </p>
    )
  }
  if (!user) {
    return <p className="text-center text-sm text-gray-500">Loading profile...</p>
  }

  return (
    <div className="mx-auto max-w-md space-y-6">
      <ProfileDetailsCard
        user={user}
        editing={editing}
        onEdit={() => setEditing(true)}
      />
      {editing && (
        <EditAccountCard
          user={user}
          onSaved={(updated) => {
            setUser(updated)
            setEditing(false)
          }}
          onCancel={() => setEditing(false)}
        />
      )}
      <ChangePasswordCard />
      <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Danger Zone</h2>
        <button
          type="button"
          onClick={() => setConfirmingDelete(true)}
          className="mt-4 w-full rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
        >
          Delete Account...
        </button>
      </section>
      {confirmingDelete && (
        <DeleteAccountModal
          onCancel={() => {
            setConfirmingDelete(false)
            setDeleteError('')
          }}
          onConfirm={deleteAccount}
          deleting={deleting}
          error={deleteError}
        />
      )}
    </div>
  )
}
