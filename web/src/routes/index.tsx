// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: "/" placeholder page until the Dashboard is built.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/')({
  component: Home,
})

function Home() {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h1 className="text-xl font-semibold text-gray-900">
        Dashboard coming soon
      </h1>
      <p className="mt-2 text-sm text-gray-600">
        This is the shared app shell scaffold (issue #108). Domain pages are
        added by each service owner under <code>src/routes/</code> — see{' '}
        <code>src/features/README.md</code> for the conventions.
      </p>
    </section>
  )
}
