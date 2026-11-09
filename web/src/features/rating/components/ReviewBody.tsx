// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-11-02, PR #286 review (@Sinnez1).
// Scope: one review as its reader sees it — the stars and when it was
// given, then the write-up with its line breaks kept and long unbroken
// strings wrapped. Extracted from the public profile's review cards
// (#257) when the Your Review dialog (#284) grew an identical copy, so
// a fix to review rendering only has to happen once. Empty text counts
// as no written review — the service trims only the ends, but a falsy
// check costs nothing and never shows a blank paragraph.
// Author review: [pending]

import { formatDateTime } from '@/features/order/status'
import { StarDisplay } from './StarDisplay'

interface ReviewBodyProps {
  stars: number
  text: string | null
  createdAt: string
}

export function ReviewBody({ stars, text, createdAt }: ReviewBodyProps) {
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <StarDisplay value={stars} />
        <span className="text-xs text-gray-400">
          {formatDateTime(createdAt)}
        </span>
      </div>
      {text ? (
        <p className="mt-2 text-sm break-words whitespace-pre-line text-gray-700">
          {text}
        </p>
      ) : (
        <p className="mt-2 text-sm text-gray-400 italic">No written review</p>
      )}
    </div>
  )
}
