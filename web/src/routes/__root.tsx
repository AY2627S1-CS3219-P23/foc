// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: root route — mounts the shared app shell; 404 handling.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createRootRoute } from '@tanstack/react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'

export const Route = createRootRoute({
  component: AppShell,
  // Renders inside the shell, so the nav stays visible on unknown paths.
  notFoundComponent: NotFound,
})
