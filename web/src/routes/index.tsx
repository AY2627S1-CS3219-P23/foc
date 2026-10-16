// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: the SPA's single route table — every page hangs off the
// shared app shell here; 404 handling.
// 2026-09-23, Claude Code (Opus 5.5): /admin route added (issue #113).
// 2026-09-28, Claude Code (Opus 5.5): merged with PR #139's auth routes;
// /admin placed inside ProtectedRoute, the 404 route moved back outside it.
// 2026-09-29, Claude Code (Opus 5.5), issue #147: /admin also behind
// AdminRoute (ADMIN or OWNER only, role from GET /users/me).
// 2026-09-29, Claude Code (Fable 5): /profile route added (issue #112).
// 2026-10-07, Claude Code (Opus 5), issue #112: /admin's AdminRoute
// wrapper restored — this branch had flattened it while adding /profile,
// which silently dropped the role gate main added in issue #147.
// 2026-10-12, Claude Code (Fable 5), issue #67: NotificationProvider
// layout route between AuthProvider and the shell — the app's single
// STOMP client (D2), below the auth it needs, above the bell that
// reads it.
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-14, Claude Code (Opus 5), PR #182 review (@Sinnez1): the
// ProtectedRoute branch carries handle.requiresSession, so AuthProvider
// can tell a session-gated page from a public one when a 401 forces a
// sign-out (owner decision: clear the session in place on public pages,
// no redirect). Declared here because this is where the gating lives.
// Author review: Leong Wei Zhi to review via the PR.

import { createBrowserRouter, type RouteObject } from 'react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'
import { Admin } from './admin'
import { Home } from './home'
import { Profile } from './profile'
import { Suppliers } from './suppliers'
import { AdminRoute } from '../features/user/AdminRoute'
import { ProtectedRoute } from '../features/user/ProtectedRoute'
import { Login } from '../features/user/login'
import { Register } from '../features/user/register'
import { AuthProvider } from '../features/user/AuthProvider'
import { NotificationProvider } from '../features/notifications/NotificationProvider'

// RouteObject.handle for the session-gated branch, read via useMatches
// (AuthProvider). ProtectedRoute is what enforces it; this only lets
// other code ask whether the current page is behind it.
export interface RouteHandle {
  requiresSession?: boolean
}

// One page = one file in this folder + one child entry below, so
// five owners adding pages touch one line each here. Loaders/actions
// are optional per route — each owner picks their own data patterns.
export const routes: RouteObject[] = [
  {
    Component: AuthProvider,
    children: [
      {
        Component: NotificationProvider,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Home /> },
              { path: 'login', element: <Login /> },
              { path: 'register', element: <Register /> },
              {
                Component: ProtectedRoute,
                handle: { requiresSession: true } satisfies RouteHandle,
                children: [
                  { path: 'suppliers', element: <Suppliers /> },
                  { path: 'profile', element: <Profile /> },
                  {
                    Component: AdminRoute,
                    children: [{ path: 'admin', element: <Admin /> }],
                  },
                ],
              },
              // Renders inside the shell, so the nav stays visible on
              // unknown paths (including nav destinations not built yet).
              // Outside ProtectedRoute: a 404 needs no login.
              { path: '*', element: <NotFound /> },
            ],
          },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
