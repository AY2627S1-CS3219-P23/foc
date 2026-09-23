// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: admin "Add Credits" dialog, per
// web/docs/wireframes/admin-dashboard.png (Users table action).
// Reviewed by: [pending]

import { useState, type FormEvent } from 'react'

import { Modal } from '@/shared/components/Modal'

interface AddCreditsModalProps {
  username: string
  onCancel: () => void
  onConfirm: (amount: number) => void
  saving?: boolean
}

export function AddCreditsModal({
  username,
  onCancel,
  onConfirm,
  saving,
}: AddCreditsModalProps) {
  const [amount, setAmount] = useState('')

  const parsed = Number(amount)
  const valid = amount !== '' && Number.isInteger(parsed) && parsed > 0

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (valid) onConfirm(parsed)
  }

  return (
    <Modal title="Add Credits" onClose={onCancel}>
      <form onSubmit={handleSubmit}>
        <label className="block text-sm text-gray-600">
          Credits to add to <strong>{username}</strong>
          <input
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            autoFocus
            className="mt-2 w-full rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-900 focus:border-gray-400 focus:outline-none"
          />
        </label>
        {amount !== '' && !valid && (
          <p className="mt-2 text-sm text-red-600">
            Enter a whole number greater than 0.
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
            type="submit"
            disabled={!valid || saving}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {saving ? 'Adding...' : 'Add Credits'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
