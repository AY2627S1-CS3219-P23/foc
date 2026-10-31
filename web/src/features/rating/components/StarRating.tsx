// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: the 1–5 star input for the rating dialog
// (web/docs/wireframes/rating-review.png — its struck-through fifth star
// is wireframe styling, all five render alike). Five native radios,
// visually hidden, labelled "1 star" … "5 stars", so keyboard arrows and
// screen readers work without bespoke key handling; the glyphs fill up
// to the chosen value.
// 2026-10-31, PR #278 review: a disabled mode for the dialog's locked
// (already-rated) state.
// Author review: [pending]

const STARS = [1, 2, 3, 4, 5] as const

interface StarRatingProps {
  value: number | null
  onChange: (stars: number) => void
  disabled?: boolean
}

export function StarRating({
  value,
  onChange,
  disabled = false,
}: StarRatingProps) {
  return (
    <div className="flex gap-1">
      {STARS.map((n) => (
        <label key={n} className={disabled ? undefined : 'cursor-pointer'}>
          <input
            type="radio"
            name="rating-stars"
            value={n}
            checked={value === n}
            disabled={disabled}
            onChange={() => onChange(n)}
            aria-label={n === 1 ? '1 star' : `${n} stars`}
            className="peer sr-only"
          />
          <span
            aria-hidden="true"
            className={`text-3xl peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-gray-900 ${
              value !== null && n <= value ? 'text-gray-900' : 'text-gray-300'
            }`}
          >
            {value !== null && n <= value ? '★' : '☆'}
          </span>
        </label>
      ))}
    </div>
  )
}
