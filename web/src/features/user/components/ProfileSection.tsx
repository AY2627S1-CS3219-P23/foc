// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: the profile page's card stack, per
// web/docs/wireframes/profile.png (the mobile column's layout at every
// size; the desktop My Settings sidebar was dropped — it has no second
// page to link yet). Loads GET /users/me, coordinates the edit card,
// and runs the delete flow: DELETE /users/me then logout(), which
// clears the stored session and navigates home.
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): owns the
// pending email change #92's PATCH can start (PR #157). user-service has
// no GET for the parked row, so the snapshot of it lives in
// localStorage (author's call via options Q&A): a reload mid-confirm
// still offers the code step instead of making the user redo the PATCH
// and pay for a fresh gate code. A snapshot the server no longer honours
// fails honestly — verify/resend answer with a type URI that says so,
// and the snapshot is dropped then.
// Reviewed by: [pending]

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { AdminUser, PendingEmailChange } from '../types'
import { useAuth } from '../useAuth'
import { useLocalStorage } from '../useLocalStorage'
import { ChangePasswordCard } from './ChangePasswordCard'
import { DeleteAccountModal } from './DeleteAccountModal'
import { EditAccountCard } from './EditAccountCard'
import { EmailChangeModal } from './EmailChangeModal'
import { ProfileDetailsCard } from './ProfileDetailsCard'

const PENDING_EMAIL_KEY = 'pendingEmailChange'

export function ProfileSection() {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loadError, setLoadError] = useState('')
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const [emailError, setEmailError] = useState('')
  const [emailNotice, setEmailNotice] = useState('')
  const [pending, setPending] =
    useLocalStorage<PendingEmailChange>(PENDING_EMAIL_KEY)
  const [codeOpen, setCodeOpen] = useState(false)
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

  // a snapshot whose code has died needs no call to know it is useless
  useEffect(() => {
    if (pending && pending.expiresAt <= Date.now()) setPending(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    return (
      <p className="text-center text-sm text-gray-500">Loading profile...</p>
    )
  }

  return (
    <div className="mx-auto max-w-md space-y-6">
      {emailError && (
        <p
          role="alert"
          className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {emailError}
        </p>
      )}
      {emailNotice && (
        <p
          role="status"
          className="rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700"
        >
          {emailNotice}
        </p>
      )}
      <ProfileDetailsCard
        user={user}
        editing={editing}
        onEdit={() => setEditing(true)}
        pendingEmail={pending?.email}
        onEnterCode={() => setCodeOpen(true)}
      />
      {editing && (
        <EditAccountCard
          user={user}
          onSaved={(updated) => {
            setUser(updated)
            setEditing(false)
          }}
          onEmailPending={(accepted) => {
            // the 202 body carries the account as it stands, so a
            // username changed by the same PATCH is already in
            setUser(accepted.user)
            setPending({
              email: accepted.email,
              expiresAt: Date.now() + accepted.expiresInSeconds * 1000,
              resendAt: Date.now() + accepted.resendInSeconds * 1000,
            })
            setEmailError('')
            setEmailNotice('')
            setEditing(false)
            setCodeOpen(true)
          }}
          onCancel={() => setEditing(false)}
        />
      )}
      <ChangePasswordCard email={user.email} />
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
      {pending && codeOpen && (
        <EmailChangeModal
          pending={pending}
          // closing keeps the pending change: the code already emailed
          // can still be entered from the details card
          onClose={() => setCodeOpen(false)}
          onVerified={(updated) => {
            setUser(updated)
            setPending(null)
            setCodeOpen(false)
            setEmailNotice('Your email address has been updated.')
          }}
          onResent={setPending}
          onDiscarded={(message) => {
            setPending(null)
            setCodeOpen(false)
            setEmailError(message)
          }}
        />
      )}
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
