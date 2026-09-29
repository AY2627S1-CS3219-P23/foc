// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: remove-account confirmation for the admin user-management
// screen, following features/supplier/components/DeleteSupplierModal.
// 2026-09-29 (issue #147): built on the shared ConfirmModal; confirm
// disabled while the list reloads.
// Reviewed by: Ryan Ang

import { ConfirmModal } from '@/shared/components/ConfirmModal'
import type { AdminUser } from '../types'

interface RemoveUserModalProps {
  user: AdminUser
  onCancel: () => void
  onConfirm: () => void
  removing?: boolean
  // confirm off while the list reloads
  disabled?: boolean
}

export function RemoveUserModal({
  user,
  onCancel,
  onConfirm,
  removing,
  disabled,
}: RemoveUserModalProps) {
  return (
    <ConfirmModal
      title="Remove Account"
      confirmLabel="Remove"
      busyLabel="Removing..."
      busy={removing}
      disabled={disabled}
      danger
      onCancel={onCancel}
      onConfirm={onConfirm}
    >
      <p className="text-sm text-gray-600">
        Are you sure you want to remove <strong>{user.username}</strong>'s
        account ({user.email})?
      </p>
    </ConfirmModal>
  )
}
