// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the public profile's "Report User" dialog, posting /reports
// with rating-service's fixed reason list. Structure follows
// DisputeModal.tsx, with two contract-driven differences: details stay
// optional even for Other (the service requires only a reason), and
// there is no requestId — the profile has no order context. The service
// keeps every submission (no duplicate detection), so nothing here
// pretends a repeat report conflicts.
// 2026-10-31, Claude Code (Fable 5), PR #279 review (@Sinnez1): the
// dialog cannot be closed while the POST is in flight (Cancel, the X
// and the backdrop included) — closing mid-request still filed the
// report, then confirmed or errored against a dialog already gone
// (RateExperienceModal, PR #278 review).
// Author review: [pending]

import { type FormEvent, useState } from 'react'

import { Banner } from '@/features/order/components/Banner'
import { errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { ratingApi } from '../api'
import {
  REPORT_DETAILS_MAX,
  reportReasonLabels,
  type ReportReason,
} from '../types'

const REASONS = Object.keys(reportReasonLabels) as ReportReason[]

interface ReportUserModalProps {
  reportedUserId: string
  reportedUsername: string
  onClose: () => void
  // after rating-service accepted the report
  onReported: () => void
}

export function ReportUserModal({
  reportedUserId,
  reportedUsername,
  onClose,
  onReported,
}: ReportUserModalProps) {
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [details, setDetails] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmed = details.trim()
  const reasonError = submitted && reason === null ? 'Choose a reason.' : null
  const detailsError =
    details.length > REPORT_DETAILS_MAX
      ? `Keep the details to ${REPORT_DETAILS_MAX} characters.`
      : null

  async function submit(e: FormEvent) {
    e.preventDefault()
    // the button is disabled, but Enter still submits a form
    if (busy) return
    setSubmitted(true)
    if (reason === null || details.length > REPORT_DETAILS_MAX) return
    setBusy(true)
    setError(null)
    try {
      await ratingApi.submitReport({
        reportedUserId,
        reason,
        ...(trimmed ? { details: trimmed } : {}),
      })
      onReported()
    } catch (err) {
      setError(errorMessage(err, 'Could not submit the report. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  // The X and the backdrop route through here too: closing mid-submit
  // would file the report anyway and land its outcome on a closed dialog.
  const requestClose = () => {
    if (!busy) onClose()
  }

  return (
    <Modal title="Report User" onClose={requestClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-gray-600">
          Reporting{' '}
          <span className="font-semibold text-gray-900">
            {reportedUsername}
          </span>
          . An admin will review the report.
        </p>

        {error && <Banner kind="error">{error}</Banner>}

        <fieldset>
          <legend className="text-sm font-medium text-gray-900">
            What happened?
          </legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {REASONS.map((r) => (
              <label
                key={r}
                className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm ${
                  reason === r
                    ? 'border-gray-900 bg-gray-50 text-gray-900'
                    : 'border-gray-200 text-gray-700 hover:bg-gray-50'
                }`}
              >
                <input
                  type="radio"
                  name="report-reason"
                  value={r}
                  checked={reason === r}
                  onChange={() => setReason(r)}
                />
                {reportReasonLabels[r]}
              </label>
            ))}
          </div>
          {reasonError && (
            <p className="mt-1 text-xs text-red-600">{reasonError}</p>
          )}
        </fieldset>

        <div>
          <label
            htmlFor="report-details"
            className="text-sm font-medium text-gray-900"
          >
            Details{' '}
            <span className="font-normal text-gray-500">(optional)</span>
          </label>
          <textarea
            id="report-details"
            value={details}
            onChange={(e) => setDetails(e.target.value)}
            rows={4}
            aria-invalid={detailsError !== null}
            aria-describedby="report-details-count"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
          />
          <div className="flex justify-between gap-2 text-xs">
            <span className="text-red-600">{detailsError}</span>
            <span
              id="report-details-count"
              className={
                details.length > REPORT_DETAILS_MAX
                  ? 'text-red-600'
                  : 'text-gray-500'
              }
            >
              {details.length}/{REPORT_DETAILS_MAX}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
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
            {busy ? 'Reporting…' : 'Report user'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
