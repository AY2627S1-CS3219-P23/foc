// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: shared app shell — top bar, routed content outlet, and the
// mobile bottom tab bar (per web/docs/wireframes/).
// Reviewed by: Leong Wei Zhi (via pull request).

import { Outlet } from 'react-router'

import { NavBar } from './NavBar'
import { TabBar } from './TabBar'

export function AppShell() {
  return (
    <div className="min-h-dvh bg-gray-50 text-gray-900">
      <NavBar />
      {/* Bottom padding on mobile keeps content clear of the fixed tab bar */}
      <main className="mx-auto max-w-6xl px-4 py-6 pb-24 md:pb-6">
        <Outlet />
      </main>
      <TabBar />
    </div>
  )
}
