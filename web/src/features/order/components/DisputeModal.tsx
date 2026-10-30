// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-29, issue #241.
// Scope: the requester's "Report a problem" dialog (team decisions DSP-5
// and DSP-7, docs/disputes.md): a required category, and a note of at
// most 500 characters that is required for Other. It posts /dispute and
// shows order-service's 400 / 409 inside the dialog, so the requester can
// correct the form or see that the window has closed.
// Author review: Ryan Ang, pending pull request review.

import { type FormEvent, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { orderApi } from '../api'
import { credits, disputeCategoryLabels, MAX_DISPUTE_NOTE } from '../status'
import type { DisputeCategory, RequestSummary } from '../types'
import { Banner } from './Banner'

const CATEGORIES = Object.keys(disputeCategoryLabels) as DisputeCategory[]

interface DisputeModalProps {
  request: RequestSummary
  // attempted: a /dispute call was made, so the request may have moved on
  onClose: (attempted: boolean) => void
  // after order-service accepted the dispute
  onReported: () => void
}

export function DisputeModal({
  request,
  onClose,
  onReported,
}: DisputeModalProps) {
  const [category, setCategory] = useState<DisputeCategory | null>(null)
  const [note, setNote] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmed = note.trim()
  const categoryError =
    submitted && category === null ? 'Choose what went wrong.' : null
  const noteError =
    note.length > MAX_DISPUTE_NOTE
      ? `Keep the note to ${MAX_DISPUTE_NOTE} characters.`
      : submitted && category === 'OTHER' && trimmed === ''
        ? 'Describe the problem.'
        : null

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    if (
      category === null ||
      note.length > MAX_DISPUTE_NOTE ||
      (category === 'OTHER' && trimmed === '')
    )
      return
    setAttempted(true)
    setBusy(true)
    setError(null)
    try {
      await orderApi.dispute(request.id, {
        category,
        ...(trimmed ? { note: trimmed } : {}),
      })
      onReported()
    } catch (err) {
      setError(errorMessage(err, 'Could not report the problem. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Report a problem" onClose={() => onClose(attempted)}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-gray-600">
          Request #{request.id}: {request.item}. The reward of{' '}
          {credits(request.creditAmount)} stays held until an admin reviews the
          problem and decides how it is divided.
        </p>

        {error && <Banner kind="error">{error}</Banner>}

        <fieldset>
          <legend className="text-sm font-medium text-gray-900">
            What went wrong?
          </legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {CATEGORIES.map((c) => (
              <label
                key={c}
                className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm ${
                  category === c
                    ? 'border-gray-900 bg-gray-50 text-gray-900'
                    : 'border-gray-200 text-gray-700 hover:bg-gray-50'
                }`}
              >
                <input
                  type="radio"
                  name="dispute-category"
                  value={c}
                  checked={category === c}
                  onChange={() => setCategory(c)}
                />
                {disputeCategoryLabels[c]}
              </label>
            ))}
          </div>
          {categoryError && (
            <p className="mt-1 text-xs text-red-600">{categoryError}</p>
          )}
        </fieldset>

        <div>
          <label
            htmlFor="dispute-note"
            className="text-sm font-medium text-gray-900"
          >
            Details{' '}
            <span className="font-normal text-gray-500">
              {category === 'OTHER' ? '(required)' : '(optional)'}
            </span>
          </label>
          <textarea
            id="dispute-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={4}
            aria-invalid={noteError !== null}
            aria-describedby="dispute-note-count"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
          />
          <div className="flex justify-between gap-2 text-xs">
            <span className="text-red-600">{noteError}</span>
            <span
              id="dispute-note-count"
              className={
                note.length > MAX_DISPUTE_NOTE
                  ? 'text-red-600'
                  : 'text-gray-500'
              }
            >
              {note.length}/{MAX_DISPUTE_NOTE}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={() => onClose(attempted)}
            className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {busy ? 'Reporting…' : 'Report problem'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
