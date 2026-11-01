// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-11-01, issue #284.
// Scope: the read-only "Your Review" dialog the Rated ★n chip on Request
// History opens — the stars, the write-up and when it was submitted.
// Ratings are immutable, so no edit screen ever shows a rater the text
// they wrote; GET /ratings/mine already carries it, so nothing new is
// fetched. A sibling of RateExperienceModal rather than a mode of it:
// that one is write-shaped throughout (its stars live in local state,
// and it calls the request detail just to name the counterpart). The
// page hands over the rating it already holds, so this has no loading or
// error state. Text keeps its line breaks and wraps long unbroken
// strings — the treatment the public profile's review cards get (#257).
// The dialog shape was the author's call via options Q&A, over a title
// tooltip (unreachable by keyboard and on touch, and silent when the
// review has no text) or an inline expand; not drawn in
// order-history.png.
// Author review: [pending]

import { formatDateTime } from '@/features/order/status'
import { Modal } from '@/shared/components/Modal'
import type { RatingResponse } from '../types'
import { StarDisplay } from './StarDisplay'

interface MyReviewModalProps {
  // the order-service request the rating was given for
  requestId: number
  rating: RatingResponse
  onClose: () => void
}

export function MyReviewModal({
  requestId,
  rating,
  onClose,
}: MyReviewModalProps) {
  return (
    <Modal title="Your Review" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-gray-600">Request #{requestId}</p>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <StarDisplay value={rating.stars} />
          <span className="text-xs text-gray-400">
            {formatDateTime(rating.createdAt)}
          </span>
        </div>

        {rating.text !== null ? (
          <p className="text-sm break-words whitespace-pre-line text-gray-700">
            {rating.text}
          </p>
        ) : (
          <p className="text-sm text-gray-400 italic">No written review</p>
        )}

        <p className="text-xs text-gray-500">
          Ratings can’t be changed once submitted.
        </p>

        <div className="flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            Close
          </button>
        </div>
      </div>
    </Modal>
  )
}
