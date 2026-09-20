// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: placeholder credits slot in the nav bar.
// Reviewed by: Leong Wei Zhi (via pull request).

// TODO(credit-service owner): replace the static placeholder with the
// live balance fetched via TanStack Query (`lib/api` wrapper).
export function CreditsBadge() {
  return (
    <span
      className="rounded-full bg-amber-100 px-3 py-1 text-sm font-medium text-amber-900"
      title="Credit balance (placeholder)"
    >
      — cr
    </span>
  )
}
