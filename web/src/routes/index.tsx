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
// 2026-10-16, Claude Code (Sonnet 5.5): order-service pages added inside
// ProtectedRoute — /my-requests, /my-requests/new, /requests,
// /deliveries/:id, /history.
// 2026-10-11, Claude Code (Fable 5), issue #94: /forgot-password
// (public — both of its steps run logged out) and /account-recovery
// (inside ProtectedRoute: the choice belongs to the deleted account's
// own session). A /reset-password link route existed for a day; it
// went when the owner revisited the reset credential to an OTP code.
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
// 2026-10-17, Claude Code (Fable 5), issue #66: /notifications route
// added inside ProtectedRoute — the Notification Center lists the
// session user's own rows, so it needs one.
// 2026-10-16, Claude Code (Opus 5), PR #180 main sync: the route
// table was the one conflict git could not shelve correctly — it put
// /forgot-password outside <AppShell /> and /account-recovery outside
// ProtectedRoute, which would have un-gated the recovery screen. Both
// sit where their #94 note says: the reset page public inside the
// shell, the recovery screen inside the requiresSession branch, so a
// 401 there signs out through ProtectedRoute's own bounce.
// 2026-10-16, Claude Code (Opus 5.5), issue #174: CreditBalanceProvider
// layout route below NotificationProvider (the shared balance poll needs
// the session, and the shell's badge reads it), and /credits behind
// ProtectedRoute.
// 2026-10-17, Claude Code (Opus 5), PR #199 main sync (#195 landed):
// the same mis-aligned hunk as the #180 sync above — git paired this
// branch's gated routes against main's public ones. /notifications
// stays inside ProtectedRoute, now under the CreditBalanceProvider
// layout #195 added; the public four keep their place in the shell.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-30, Claude Code (Fable 5), issue #257: /users/:id (public
// profile) inside ProtectedRoute — every API it reads wants a session.
// Reached by link (requests, deliveries), not from the nav, like
// /credits.
// Author review: [pending]

import { createBrowserRouter, type RouteObject } from 'react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'
import { ActiveDelivery } from './active-delivery'
import { Admin } from './admin'
import { CreateRequest } from './create-request'
import { MyRequests } from './my-requests'
import { OpenRequests } from './open-requests'
import { Credits } from './credits'
import { Home } from './home'
import { Notifications } from './notifications'
import { Profile } from './profile'
import { PublicProfile } from './public-profile'
import { RequestHistory } from './request-history'
import { Suppliers } from './suppliers'
import { AccountRecovery } from '../features/user/account-recovery'
import { AdminRoute } from '../features/user/AdminRoute'
import { ForgotPassword } from '../features/user/forgot-password'
import { ProtectedRoute } from '../features/user/ProtectedRoute'
import { Login } from '../features/user/login'
import { Register } from '../features/user/register'
import { RegisterOwner } from '../features/user/registerOwner'
import { AuthProvider } from '../features/user/AuthProvider'
import { NotificationProvider } from '../features/notifications/NotificationProvider'
import { CreditBalanceProvider } from '../features/credit/CreditBalanceProvider'

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
            Component: CreditBalanceProvider,
            children: [
              {
                element: <AppShell />,
                children: [
                  { index: true, element: <Home /> },
                  { path: 'login', element: <Login /> },
                  { path: 'register', element: <Register /> },
                  { path: 'register-owner', element: <RegisterOwner /> },
                  { path: 'forgot-password', element: <ForgotPassword /> },
                  {
                    Component: ProtectedRoute,
                    handle: { requiresSession: true } satisfies RouteHandle,
                    children: [
                      { path: 'suppliers', element: <Suppliers /> },
                      { path: 'notifications', element: <Notifications /> },
                      { path: 'profile', element: <Profile /> },
                      {
                        path: 'account-recovery',
                        element: <AccountRecovery />,
                      },
                      { path: 'credits', element: <Credits /> },
                      { path: 'my-requests', element: <MyRequests /> },
                      { path: 'my-requests/new', element: <CreateRequest /> },
                      { path: 'requests', element: <OpenRequests /> },
                      { path: 'deliveries/:id', element: <ActiveDelivery /> },
                      { path: 'history', element: <RequestHistory /> },
                      { path: 'users/:id', element: <PublicProfile /> },
                      {
                        Component: AdminRoute,
                        children: [{ path: 'admin', element: <Admin /> }],
                      },
                    ],
                  },
                  // Renders inside the shell, so the nav stays visible on
                  // unknown paths (including nav destinations not built
                  // yet). Outside ProtectedRoute: a 404 needs no login.
                  { path: '*', element: <NotFound /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
