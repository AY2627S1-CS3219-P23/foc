// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the green/red message banners the order pages share, styled like
// the suppliers page's.
// Author review: [pending]

export function Banner({
  kind,
  children,
}: {
  kind: 'success' | 'error'
  children: string
}) {
  const classes =
    kind === 'success'
      ? 'border-green-200 bg-green-50 text-green-700'
      : 'border-red-200 bg-red-50 text-red-700'
  return (
    <p
      role={kind === 'error' ? 'alert' : 'status'}
      className={`rounded-md border px-3 py-2 text-sm ${classes}`}
    >
      {children}
    </p>
  )
}
