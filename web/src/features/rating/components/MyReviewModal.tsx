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
// error state. The dialog shape was the author's call via options Q&A,
// over a title tooltip (unreachable by keyboard and on touch, and silent
// when the review has no text) or an inline expand; not drawn in
// order-history.png.
// 2026-11-02, Claude Code (Fable 5), PR #286 review (@Sinnez1): the
// stars/date/text block is the shared ReviewBody (it was a copy of the
// public profile's review card), and the request id comes off the
// rating itself — RatingResponse carries requestId, so the separate
// prop could only ever disagree with it.
// Author review: [pending]

import { Modal } from '@/shared/components/Modal'
import type { RatingResponse } from '../types'
import { ReviewBody } from './ReviewBody'

interface MyReviewModalProps {
  rating: RatingResponse
  onClose: () => void
}

export function MyReviewModal({ rating, onClose }: MyReviewModalProps) {
  return (
    <Modal title="Your Review" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-gray-600">Request #{rating.requestId}</p>

        <ReviewBody
          stars={rating.stars}
          text={rating.text}
          createdAt={rating.createdAt}
        />

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
