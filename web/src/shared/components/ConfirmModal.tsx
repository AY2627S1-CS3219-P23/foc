// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: shared confirm dialog — the Modal plus the Cancel / confirm
// footer that RemoveUserModal, ChangeRoleModal and DeleteSupplierModal
// each repeated.
// Author review: Ryan to review via the PR.

import type { ReactNode } from 'react'

import { Modal } from './Modal'

interface ConfirmModalProps {
  title: string
  // the question, plus any warnings
  children: ReactNode
  confirmLabel: string
  // shown on the confirm button while busy, e.g. "Removing..."
  busyLabel: string
  busy?: boolean
  // red confirm button, for destructive actions
  danger?: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function ConfirmModal({
  title,
  children,
  confirmLabel,
  busyLabel,
  busy = false,
  danger = false,
  onCancel,
  onConfirm,
}: ConfirmModalProps) {
  return (
    <Modal title={title} onClose={onCancel}>
      {children}
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
          disabled={busy}
          className={`rounded-md px-4 py-2 text-sm font-medium text-white disabled:opacity-50 ${
            danger
              ? 'bg-red-600 hover:bg-red-700'
              : 'bg-gray-900 hover:bg-gray-800'
          }`}
        >
          {busy ? busyLabel : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
