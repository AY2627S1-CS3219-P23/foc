// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: the "Rate Your Experience" dialog per
// web/docs/wireframes/rating-review.png (the wireframe's Report Review
// dialog is issue #254, not built here): required stars, optional
// write-up capped to match rating-service, problem+json errors shown in
// the dialog. The subtitle names the other party — fetched on open from
// the request's detail (ids only) plus the cached username lookup, and
// left as just "Request #id" until both answer. On the courier tab the
// counterpart is the requester, so the label flips to "Requested by".
// Structure follows DisputeModal.tsx (issue #241).
// Author review: [pending]

import { type FormEvent, useEffect, useState } from 'react'

import { orderApi } from '@/features/order/api'
import { Banner } from '@/features/order/components/Banner'
import type { RequestRole, RequestSummary } from '@/features/order/types'
import { useUsername } from '@/features/order/useUsername'
import { ApiError, errorMessage } from '@/lib/api/http'
import { Modal } from '@/shared/components/Modal'
import { ratingApi } from '../api'
import type { RatingResponse } from '../types'
import { MAX_RATING_TEXT } from '../types'
import { StarRating } from './StarRating'

interface RateExperienceModalProps {
  request: RequestSummary
  // which side of the request the viewer is on (the History tab)
  role: RequestRole
  onClose: () => void
  // rating-service accepted the submission
  onRated: (rating: RatingResponse) => void
  // 409: rated before (another tab/session) — the row's cached
  // "not-rated" is stale and needs a refetch
  onAlreadyRated: () => void
}

export function RateExperienceModal({
  request,
  role,
  onClose,
  onRated,
  onAlreadyRated,
}: RateExperienceModalProps) {
  const [stars, setStars] = useState<number | null>(null)
  const [text, setText] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [counterpartId, setCounterpartId] = useState<number | null>(null)

  // ids only; a failed lookup just leaves the shorter subtitle
  useEffect(() => {
    let cancelled = false
    orderApi.getRequest(request.id).then(
      (detail) => {
        if (!cancelled)
          setCounterpartId(
            role === 'requester' ? detail.courierId : detail.requesterId,
          )
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [request.id, role])
  const counterpartName = useUsername(counterpartId)

  const trimmed = text.trim()
  const starsError =
    submitted && stars === null ? 'Choose a star rating.' : null
  const textError =
    text.length > MAX_RATING_TEXT
      ? `Keep the review to ${MAX_RATING_TEXT} characters.`
      : null

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    if (stars === null || text.length > MAX_RATING_TEXT) return
    setBusy(true)
    setError(null)
    try {
      const rating = await ratingApi.submitRating({
        requestId: String(request.id),
        stars,
        ...(trimmed ? { text: trimmed } : {}),
      })
      onRated(rating)
    } catch (err) {
      setError(errorMessage(err, 'Could not submit your rating. Try again.'))
      if (err instanceof ApiError && err.status === 409) onAlreadyRated()
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Rate Your Experience" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <p className="text-sm text-gray-600">
          Request #{request.id}
          {counterpartName &&
            ` — ${role === 'requester' ? 'Delivered' : 'Requested'} by ${counterpartName}`}
        </p>

        {error && <Banner kind="error">{error}</Banner>}

        <fieldset>
          <legend className="text-sm font-medium text-gray-900">
            Your rating
          </legend>
          <div className="mt-2">
            <StarRating value={stars} onChange={setStars} />
          </div>
          {starsError && (
            <p className="mt-1 text-xs text-red-600">{starsError}</p>
          )}
        </fieldset>

        <div>
          <label
            htmlFor="rating-text"
            className="text-sm font-medium text-gray-900"
          >
            Write a review{' '}
            <span className="font-normal text-gray-500">(optional)</span>
          </label>
          <textarea
            id="rating-text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={4}
            aria-invalid={textError !== null}
            aria-describedby="rating-text-count"
            className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
          />
          <div className="flex justify-between gap-2 text-xs">
            <span className="text-red-600">{textError}</span>
            <span
              id="rating-text-count"
              className={
                text.length > MAX_RATING_TEXT ? 'text-red-600' : 'text-gray-500'
              }
            >
              {text.length}/{MAX_RATING_TEXT}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {busy ? 'Submitting…' : 'Submit Review'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
