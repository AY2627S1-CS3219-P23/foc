// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: remove-account confirmation for the admin user-management
// screen, following features/supplier/components/DeleteSupplierModal.
// Reviewed by: Ryan Ang

import { Modal } from '@/shared/components/Modal'
import type { AdminUser } from '../types'

interface RemoveUserModalProps {
  user: AdminUser
  onCancel: () => void
  onConfirm: () => void
  removing?: boolean
}

export function RemoveUserModal({
  user,
  onCancel,
  onConfirm,
  removing,
}: RemoveUserModalProps) {
  return (
    <Modal title="Remove Account" onClose={onCancel}>
      <p className="text-sm text-gray-600">
        Are you sure you want to remove <strong>{user.username}</strong>'s
        account ({user.email})?
      </p>
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
          disabled={removing}
          className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
        >
          {removing ? 'Removing...' : 'Remove'}
        </button>
      </div>
    </Modal>
  )
}
