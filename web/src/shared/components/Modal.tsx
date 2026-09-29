// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: shared modal primitive — first one built in this folder, per
// src/shared/components/README.md ("whoever needs one first builds it
// here"). Needed by the supplier add/edit and delete-confirm dialogs.
// 2026-09-30, Claude Code (Opus 5.5): closeOnBackdrop, so a form can opt
// out of closing on a click outside it (only its Cancel / ✕ close it).
// Reviewed by: [pending]

import type { ReactNode } from 'react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  // false: a click on the backdrop does nothing, so a half-filled form
  // isn't lost to a stray click; the ✕ (and the caller's Cancel) still close
  closeOnBackdrop?: boolean
}

export function Modal({
  title,
  onClose,
  children,
  closeOnBackdrop = true,
}: ModalProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/40 p-4"
      onClick={closeOnBackdrop ? onClose : undefined}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
