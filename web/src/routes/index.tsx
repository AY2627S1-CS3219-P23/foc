// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: the SPA's single route table — every page hangs off the
// shared app shell here; 404 handling.
// 2026-09-23, Claude Code (Opus 5.5): /admin route added (issue #113).
// Reviewed by: Leong Wei Zhi (via pull request).

import type { RouteObject } from 'react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'
import { Admin } from './admin'
import { Home } from './home'
import { Suppliers } from './suppliers'

// One page = one file in this folder + one child entry below, so
// five owners adding pages touch one line each here. Loaders/actions
// are optional per route — each owner picks their own data patterns.
export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Home /> },
      { path: 'suppliers', element: <Suppliers /> },
      { path: 'admin', element: <Admin /> },
      // Renders inside the shell, so the nav stays visible on
      // unknown paths (including nav destinations not built yet).
      { path: '*', element: <NotFound /> },
    ],
  },
]
