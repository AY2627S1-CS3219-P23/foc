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
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): the page
// no longer asks GET /users/me for itself. AuthProvider already holds
// that answer for the session as `me`, so this was a second request for
// the same thing — and its private copy meant an edit here left the nav
// bar and the /admin guard showing the pre-edit account until the next
// login. Reads `me`, and hands every save back through updateMe.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: the snapshot
// names its owner and only the signed-in account's own is read, shown or
// acted on. One browser-wide key outlives the session that wrote it
// (logout and account deletion leave it behind), so an unowned snapshot
// let the next account to sign in see — and confirm, against its own
// account — an address somebody else had parked.
// Author review: Leong Wei Zhi (via PR #149).

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { profileApi } from '../profileApi'
import type { PendingEmailChange } from '../types'
import { useAuth } from '../useAuth'
import { useLocalStorage } from '../useLocalStorage'
import { ChangePasswordCard } from './ChangePasswordCard'
import { DeleteAccountModal } from './DeleteAccountModal'
import { EditAccountCard } from './EditAccountCard'
import { EmailChangeModal } from './EmailChangeModal'
import { ProfileDetailsCard } from './ProfileDetailsCard'

const PENDING_EMAIL_KEY = 'pendingEmailChange'

export function ProfileSection() {
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const [emailError, setEmailError] = useState('')
  const [emailNotice, setEmailNotice] = useState('')
  const [stored, setStored] =
    useLocalStorage<PendingEmailChange>(PENDING_EMAIL_KEY)
  const [codeOpen, setCodeOpen] = useState(false)
  // the session's own GET /users/me answer, fetched once by AuthProvider
  const { me, logout, updateMe } = useAuth()
  const user = me?.status === 'ready' ? me.user : null

  // The snapshot this page may use: the signed-in account's own. One
  // belonging to another account is left in storage — it is still its
  // owner's change to finish — but never read from here, so neither the
  // details card nor the confirm dialog can offer somebody else's
  // address, or act on it as this account's (PR #149 review).
  const pending = user && stored?.userId === user.id ? stored : null

  // A snapshot nobody can use needs no call to know it: its code has
  // died, or it names no owner (written before snapshots were scoped),
  // so no account may claim it.
  useEffect(() => {
    if (!stored) return
    if (stored.expiresAt <= Date.now() || typeof stored.userId !== 'number')
      setStored(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const deleteAccount = async () => {
    setDeleting(true)
    setDeleteError('')
    try {
      await profileApi.deleteAccount()
      // the account is gone, and so is any email change it had parked
      if (pending) setStored(null)
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

  if (me?.status === 'error') {
    return (
      <p
        role="alert"
        className="mx-auto max-w-md rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
      >
        {me.message}
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
            updateMe(updated)
            setEditing(false)
          }}
          onEmailPending={(accepted) => {
            // the 202 body carries the account as it stands, so a
            // username changed by the same PATCH is already in
            updateMe(accepted.user)
            setStored({
              // the 202's own account, not this page's copy of it
              userId: accepted.user.id,
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
            updateMe(updated)
            setStored(null)
            setCodeOpen(false)
            // the banners are a pair: a success clears whatever the last
            // failure left up (PR #149 review, @Sinnez1)
            setEmailError('')
            setEmailNotice('Your email address has been updated.')
          }}
          onResent={setStored}
          onDiscarded={(message) => {
            setStored(null)
            setCodeOpen(false)
            setEmailNotice('')
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
