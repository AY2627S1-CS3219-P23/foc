// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-28, issue #113.
// Scope: promote/demote confirmation for the admin user-management
// screen, following RemoveUserModal. Revised 2026-09-29 (PR #140
// review): when the signed-in admin is unknown, every demotion carries a
// general warning, so a failed GET /users/me can't hide the self-demotion
// one.
// Reviewed by: Ryan Ang

import { Modal } from '@/shared/components/Modal'
import type { AdminUser, UserRole } from '../types'

interface ChangeRoleModalProps {
  user: AdminUser
  role: UserRole
  // null: the signed-in admin couldn't be identified
  isSelf: boolean | null
  onCancel: () => void
  onConfirm: () => void
  saving?: boolean
}

export function ChangeRoleModal({
  user,
  role,
  isSelf,
  onCancel,
  onConfirm,
  saving,
}: ChangeRoleModalProps) {
  const promoting = role === 'ADMIN'

  return (
    <Modal
      title={promoting ? 'Promote to Admin' : 'Demote to User'}
      onClose={onCancel}
    >
      <p className="text-sm text-gray-600">
        {promoting
          ? 'Are you sure you want to make '
          : 'Are you sure you want to remove admin access from '}
        <strong>{user.username}</strong> ({user.email})
        {promoting ? ' an admin?' : '?'}
      </p>
      {isSelf === true && !promoting && (
        <p className="mt-2 text-sm font-medium text-red-700">
          This is your own account. You will lose access to the admin dashboard.
        </p>
      )}
      {isSelf === null && !promoting && (
        <p className="mt-2 text-sm font-medium text-red-700">
          If this is your own account, you will lose access to the admin
          dashboard.
        </p>
      )}
      <div className="mt-5 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={saving}
          className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {saving ? 'Saving...' : promoting ? 'Promote' : 'Demote'}
        </button>
      </div>
    </Modal>
  )
}
