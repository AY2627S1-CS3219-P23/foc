// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: delete-account confirmation for the profile page, following
// RemoveUserModal; the copy warns about the 30-day lock (F3.1.1),
// matching user-service's soft delete + purge scheduler.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): the
// dialog cannot be dismissed while the DELETE is in flight. Cancel (and
// the ✕ / backdrop, which share its handler) used to close the modal
// over a request that still went through — logout() then signed the user
// out with no warning, and a failure put its message on a dialog that
// was no longer there.
// Author review: Leong Wei Zhi (via PR #149).

import { Modal } from '@/shared/components/Modal'

interface DeleteAccountModalProps {
  onCancel: () => void
  onConfirm: () => void
  deleting?: boolean
  error?: string
}

export function DeleteAccountModal({
  onCancel,
  onConfirm,
  deleting,
  error,
}: DeleteAccountModalProps) {
  // nothing dismisses the dialog once the request is away: there is no
  // call to cancel, and its answer has to land somewhere
  const dismiss = deleting ? () => {} : onCancel

  return (
    <Modal title="Are you sure?" onClose={dismiss}>
      {error && (
        <p
          role="alert"
          className="mb-4 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      <p className="text-sm text-gray-600">
        Your account will be locked for 30 days. You cannot create a new account
        with the same email or username during this period. After 30 days, your
        account and all data will be permanently deleted.
      </p>
      <div className="mt-5 flex justify-end gap-2">
        <button
          type="button"
          onClick={dismiss}
          disabled={deleting}
          className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={deleting}
          className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
        >
          {deleting ? 'Deleting...' : 'Delete My Account'}
        </button>
      </div>
    </Modal>
  )
}
