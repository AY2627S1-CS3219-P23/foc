// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: delete confirmation, per
// web/docs/wireframes/add-edit-supplier.png. Implements F1.1.3.
// 2026-09-29 (Claude Code, Opus 5.5, issue #147): built on the shared
// ConfirmModal; text and behaviour unchanged.
// Reviewed by: Ryan Ang (the 2026-09-29 issue #147 changes above); the
// original supplier code's review is still pending with its author.

import { ConfirmModal } from '@/shared/components/ConfirmModal'
import type { Supplier } from '../types'

interface DeleteSupplierModalProps {
  supplier: Supplier
  onCancel: () => void
  onConfirm: () => void
  deleting?: boolean
}

export function DeleteSupplierModal({
  supplier,
  onCancel,
  onConfirm,
  deleting,
}: DeleteSupplierModalProps) {
  return (
    <ConfirmModal
      title="Delete Supplier"
      confirmLabel="Delete"
      busyLabel="Deleting..."
      busy={deleting}
      danger
      onCancel={onCancel}
      onConfirm={onConfirm}
    >
      <p className="text-sm text-gray-600">
        Are you sure you want to delete <strong>{supplier.name}</strong>? This
        action cannot be undone.
      </p>
    </ConfirmModal>
  )
}
