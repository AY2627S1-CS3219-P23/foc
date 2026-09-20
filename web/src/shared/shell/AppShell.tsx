// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: shared app shell — nav + routed content outlet.
// Reviewed by: Leong Wei Zhi (via pull request).

import { Outlet } from '@tanstack/react-router'

import { NavBar } from './NavBar'

export function AppShell() {
  return (
    <div className="min-h-dvh bg-gray-50 text-gray-900">
      <NavBar />
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
