// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-30, PR #156 review.
// Scope: the success line's 4s auto-dismiss, moved here from
// routes/suppliers.tsx and SuppliersAdminSection.tsx (they were verbatim
// copies).
// Author review: Ryan to review via the PR.

import { useEffect } from 'react'

export const AUTO_DISMISS_MS = 4000

// Clears a success message a few seconds after it appears: it only
// describes the last action, and lingering just clutters the page once
// the user has moved on. Errors are not passed here; they stay until the
// next action.
export function useAutoDismissed(
  value: string | null,
  setValue: (value: null) => void,
) {
  useEffect(() => {
    if (!value) return
    const timeout = window.setTimeout(() => setValue(null), AUTO_DISMISS_MS)
    return () => window.clearTimeout(timeout)
  }, [value, setValue])
}
