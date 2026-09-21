// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: placeholder credits slot in the top bar (wireframes show
// "Available: N Credits").
// Reviewed by: Leong Wei Zhi (via pull request).

// TODO(credit-service owner): replace the static placeholder with the
// live balance fetched through the `lib/api` wrapper.
export function CreditsBadge() {
  return (
    <span
      className="rounded-md border border-gray-300 bg-white px-3 py-1 text-sm font-medium whitespace-nowrap"
      title="Credit balance (placeholder)"
    >
      Available: — Credits
    </span>
  )
}
