// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: the SPA's single route table — every page hangs off the
// shared app shell here; 404 handling.
// Reviewed by: Leong Wei Zhi (via pull request).

import type { RouteObject } from 'react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'
import { Home } from './home'

// One page = one file in this folder + one child entry below, so
// five owners adding pages touch one line each here. Loaders/actions
// are optional per route — each owner picks their own data patterns.
export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Home /> },
      // Renders inside the shell, so the nav stays visible on
      // unknown paths (including nav destinations not built yet).
      { path: '*', element: <NotFound /> },
    ],
  },
]
