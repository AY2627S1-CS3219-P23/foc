// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22.
// Scope: shared modal primitive — first one built in this folder, per
// src/shared/components/README.md ("whoever needs one first builds it
// here"). Needed by the supplier add/edit and delete-confirm dialogs.
// Reviewed by: [pending]
// 2026-11-02, Claude Code (Fable 5), PR #286 review (@Sinnez1): the
// dialog caps at the viewport (minus the backdrop's p-4) and scrolls —
// long user text (a review with many line breaks) pushed the close
// buttons off-screen with no way to reach them. Escape closes (through
// the same onClose the backdrop uses, so dialogs that lock their close
// handler stay locked), and focus moves into the dialog on open unless
// a child (the OTP inputs' autoFocus) already took it.
// Author review: [pending]

import { useEffect, useRef, type ReactNode } from 'react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
}

export function Modal({ title, onClose, children }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.contains(document.activeElement)) dialog.focus()
  }, [])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/40 p-4"
      onClick={onClose}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose()
      }}
    >
      <div
        ref={dialogRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-lg border border-gray-200 bg-white p-6 shadow-lg outline-none"
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
