// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #257.
// Scope: the read-only star row for the public profile — the summary
// line and each review card. Sibling of StarRating rather than a mode
// of it: that one is an input (five native radios sharing one group
// name), so several instances on a page of reviews would join the same
// radio group; this renders plain glyphs, carries the exact value in
// an aria-label, and rounds only the fill.
// Author review: [pending]

const STARS = [1, 2, 3, 4, 5] as const

interface StarDisplayProps {
  value: number
  // lg matches the summary row's prominence; reviews use the default
  size?: 'sm' | 'lg'
}

export function StarDisplay({ value, size = 'sm' }: StarDisplayProps) {
  const filled = Math.round(value)
  return (
    <span
      role="img"
      aria-label={`${Number(value.toFixed(1))} out of 5 stars`}
      className={`inline-flex gap-0.5 ${size === 'lg' ? 'text-2xl' : 'text-base'}`}
    >
      {STARS.map((n) => (
        <span
          key={n}
          aria-hidden="true"
          className={n <= filled ? 'text-gray-900' : 'text-gray-300'}
        >
          {n <= filled ? '★' : '☆'}
        </span>
      ))}
    </span>
  )
}
