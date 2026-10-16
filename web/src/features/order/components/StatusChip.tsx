// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the small status pill shown on order cards and rows.
// Author review: [pending]

import { statusChipClasses, statusLabels } from '../status'
import type { OrderStatus } from '../types'

export function StatusChip({ status }: { status: OrderStatus }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusChipClasses[status]}`}
    >
      {statusLabels[status]}
    </span>
  )
}
