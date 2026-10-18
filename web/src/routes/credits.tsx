// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-16, issue #174.
// Scope: the /credits route (team decision: its own page, reached from
// the top-bar credits badge and the profile card, no nav item). Thin,
// per src/features/README.md: the page lives in features/credit.
// Author review: Ryan Ang, pending pull request review.

import { CreditHistoryPage } from '@/features/credit/CreditHistoryPage'

export function Credits() {
  return <CreditHistoryPage />
}
