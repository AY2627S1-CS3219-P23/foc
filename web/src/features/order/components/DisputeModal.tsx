// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the "File a Dispute" form (state 2 of dispute-flow.png) as a
// modal. The admin resolution panel (state 3) is not part of this page.
// Author review: [pending]

import { useState } from 'react'

import { Modal } from '@/shared/components/Modal'
import type { DisputeInput } from '../types'

const DISPUTE_REASONS = [
  'Wrong item delivered',
  'Item not received',
  'Item damaged',
  'Overcharged credits',
  'Other',
] as const

interface DisputeModalProps {
  orderId: number
  busy: boolean
  error: string | null
  onCancel: () => void
  onSubmit: (input: DisputeInput) => void
}

export function DisputeModal({
  orderId,
  busy,
  error,
  onCancel,
  onSubmit,
}: DisputeModalProps) {
  const [reason, setReason] = useState<string>(DISPUTE_REASONS[0])
  const [description, setDescription] = useState('')
  const [touched, setTouched] = useState(false)
  const missing = touched && !description.trim()

  return (
    <Modal title="File a dispute" onClose={busy ? () => {} : onCancel}>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          setTouched(true)
          if (description.trim())
            onSubmit({ reason, description: description.trim() })
        }}
        noValidate
      >
        <p className="text-sm text-gray-500">Regarding Request #{orderId}</p>
        <div>
          <label
            htmlFor="dispute-reason"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            Reason
          </label>
          <select
            id="dispute-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm"
          >
            {DISPUTE_REASONS.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </div>
        <div>
          <label
            htmlFor="dispute-description"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            Describe the issue
          </label>
          <textarea
            id="dispute-description"
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Please provide details about what went wrong with your delivery…"
            aria-invalid={missing}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          {missing && (
            <p className="mt-1 text-xs text-red-600">
              Please describe the issue.
            </p>
          )}
        </div>
        {error && (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {busy ? 'Submitting…' : 'Submit dispute'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
