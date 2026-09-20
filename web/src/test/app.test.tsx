// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: example shell/router tests for teammates to copy.
// Reviewed by: Leong Wei Zhi (via pull request).

import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  RouterProvider,
  createRouter,
  createMemoryHistory,
} from '@tanstack/react-router'

import { routeTree } from '../routeTree.gen'

function renderApp(initialPath: string) {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialPath] }),
  })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

test('shell renders nav, brand, credits and notification slots on /', async () => {
  renderApp('/')

  expect(await screen.findByRole('navigation')).toBeInTheDocument()
  expect(screen.getByText('Favours on Campus')).toBeInTheDocument()
  expect(screen.getByTitle('Credit balance (placeholder)')).toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: 'Notifications' }),
  ).toBeInTheDocument()
})

test('unknown path shows the 404 page inside the shell', async () => {
  renderApp('/definitely-not-a-route')

  expect(
    await screen.findByRole('heading', { name: 'Page not found' }),
  ).toBeInTheDocument()
  // The nav is still present — the 404 renders inside the app shell.
  expect(screen.getByRole('navigation')).toBeInTheDocument()
})
