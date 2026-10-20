// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the small status pill shown on order cards and rows.
// 2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: typed by order-service's
// RequestStatus.
// Author review: [pending]

import { statusChipClasses, statusLabels } from '../status'
import type { RequestStatus } from '../types'

export function StatusChip({ status }: { status: RequestStatus }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusChipClasses[status]}`}
    >
      {statusLabels[status]}
    </span>
  )
}
