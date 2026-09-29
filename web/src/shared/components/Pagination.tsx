// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-28, issue #113.
// Scope: shared pager primitive — Previous / page numbers / Next.
// First used by the admin Users section.
// Reviewed by: Ryan Ang

interface PaginationProps {
  page: number // 1-based
  totalPages: number
  onPageChange: (page: number) => void
  disabled?: boolean
}

// Long ranges collapse to first, last and the pages next to the current
// one, e.g. 1 … 4 5 6 … 12.
function pageItems(page: number, totalPages: number): (number | 'gap')[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1)
  }
  const items: (number | 'gap')[] = [1]
  const start = Math.max(2, page - 1)
  const end = Math.min(totalPages - 1, page + 1)
  if (start > 2) items.push('gap')
  for (let p = start; p <= end; p++) items.push(p)
  if (end < totalPages - 1) items.push('gap')
  items.push(totalPages)
  return items
}

const buttonClass =
  'min-w-9 rounded-md border px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50'

export function Pagination({
  page,
  totalPages,
  onPageChange,
  disabled = false,
}: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center gap-1">
      <button
        type="button"
        disabled={disabled || page <= 1}
        onClick={() => onPageChange(page - 1)}
        className={`${buttonClass} border-gray-200 bg-white text-gray-700 hover:bg-gray-50`}
      >
        Previous
      </button>
      {pageItems(page, totalPages).map((item, i) =>
        item === 'gap' ? (
          <span key={`gap-${i}`} className="px-2 text-sm text-gray-400">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            disabled={disabled}
            aria-current={item === page ? 'page' : undefined}
            aria-label={`Page ${item}`}
            onClick={() => onPageChange(item)}
            className={
              item === page
                ? `${buttonClass} border-gray-900 bg-gray-900 text-white`
                : `${buttonClass} border-gray-200 bg-white text-gray-700 hover:bg-gray-50`
            }
          >
            {item}
          </button>
        ),
      )}
      <button
        type="button"
        disabled={disabled || page >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className={`${buttonClass} border-gray-200 bg-white text-gray-700 hover:bg-gray-50`}
      >
        Next
      </button>
    </nav>
  )
}
