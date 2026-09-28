// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: the SPA's single route table — every page hangs off the
// shared app shell here; 404 handling.
// Reviewed by: Leong Wei Zhi (via pull request).

import { createBrowserRouter, type RouteObject } from 'react-router'

import { AppShell } from '@/shared/shell/AppShell'
import { NotFound } from '@/shared/shell/NotFound'
import { Home } from './home'
import { Suppliers } from './suppliers'
import { ProtectedRoute } from '../features/user/ProtectedRoute'
import { Login } from '../features/user/login'
import { Register } from '../features/user/register'
import { AuthProvider } from '../features/user/AuthProvider'
// One page = one file in this folder + one child entry below, so
// five owners adding pages touch one line each here. Loaders/actions
// are optional per route — each owner picks their own data patterns.
export const routes: RouteObject[] = [
  {
    Component: AuthProvider,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Home /> },
          { path: 'login', element: <Login /> },
          { path: 'register', element: <Register /> },
          { Component: ProtectedRoute,
            children: [
            { path: 'suppliers', element: <Suppliers /> },
            { path: '*', element: <NotFound /> },
            ],
          }
        ]
      }
    ]
  },
]

export const router = createBrowserRouter(routes)