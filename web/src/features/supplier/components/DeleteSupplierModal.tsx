// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: delete confirmation, per
// web/docs/wireframes/add-edit-supplier.png. Implements F1.1.3.
// Reviewed by: [pending]

import { Modal } from '@/shared/components/Modal'
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
    <Modal title="Delete Supplier" onClose={onCancel}>
      <p className="text-sm text-gray-600">
        Are you sure you want to delete <strong>{supplier.name}</strong>? This
        action cannot be undone.
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
          disabled={deleting}
          className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
        >
          {deleting ? 'Deleting...' : 'Delete'}
        </button>
      </div>
    </Modal>
  )
}
