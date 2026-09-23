// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: example shell/router tests for teammates to copy.
// Reviewed by: Leong Wei Zhi (via pull request).

import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { routes } from '../routes'

function renderApp(initialPath: string) {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: [initialPath] })}
    />,
  )
}

test('shell renders nav, brand, credits and notification slots on /', () => {
  renderApp('/')

  expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
  expect(screen.getByText('FoC (Favours on Campus)')).toBeInTheDocument()
  expect(screen.getByTitle('Credit balance (placeholder)')).toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: 'Notifications' }),
  ).toBeInTheDocument()
})

test('mobile tab bar lists the five nav destinations', () => {
  renderApp('/')

  const tabBar = screen.getByRole('navigation', { name: 'Main tabs' })
  for (const label of [
    'Home',
    'Suppliers',
    'My Requests',
    'Requests',
    'Profile',
  ]) {
    expect(tabBar).toHaveTextContent(label)
  }
})

test('unknown path shows the 404 page inside the shell', () => {
  renderApp('/definitely-not-a-route')

  expect(
    screen.getByRole('heading', { name: 'Page not found' }),
  ).toBeInTheDocument()
  // The nav is still present — the 404 renders inside the app shell.
  expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
})
