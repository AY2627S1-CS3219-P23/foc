// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: 404 view rendered inside the app shell.
// Reviewed by: Leong Wei Zhi (via pull request).

import { Link } from 'react-router'

export function NotFound() {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 text-center shadow-sm">
      <h1 className="text-xl font-semibold text-gray-900">Page not found</h1>
      <p className="mt-2 text-sm text-gray-600">
        The page you are looking for does not exist (or has not been built yet).
      </p>
      <Link
        to="/"
        className="mt-4 inline-block rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700"
      >
        Back to Home
      </Link>
    </section>
  )
}
