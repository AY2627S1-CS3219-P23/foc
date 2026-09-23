// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: tests for the Admin Dashboard user-management page, run
// against the in-memory adminUserApi mock.
// Reviewed by: [pending]

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { adminUserApi, resetMockUsers } from '@/features/user/adminApi'
import { routes } from '../routes'

function renderAdmin() {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/admin'] })}
    />,
  )
}

// The page renders both a table (md+) and cards (mobile); jsdom applies
// no media queries, so scope queries to the desktop table.
async function findTable() {
  return screen.findByRole('table')
}

function rowFor(table: HTMLElement, username: string) {
  const row = within(table).getByText(username).closest('tr')
  if (!row) throw new Error(`No row for ${username}`)
  return within(row)
}

beforeEach(() => {
  resetMockUsers()
  vi.restoreAllMocks()
})

test('lists users with their roles', async () => {
  renderAdmin()

  const table = await findTable()
  expect(
    screen.getByRole('heading', { name: 'Admin Dashboard' }),
  ).toBeInTheDocument()
  expect(rowFor(table, 'student_alex').getByText('User')).toBeInTheDocument()
  expect(rowFor(table, 'nus_courier_99').getByText('Admin')).toBeInTheDocument()
  expect(rowFor(table, 'foc_owner').getByText('Owner')).toBeInTheDocument()
})

test('owner row has no actions', async () => {
  renderAdmin()

  const owner = rowFor(await findTable(), 'foc_owner')
  expect(owner.queryByRole('button')).not.toBeInTheDocument()
})

test('search filters by username', async () => {
  const user = userEvent.setup()
  renderAdmin()
  const table = await findTable()

  await user.type(
    screen.getByRole('searchbox', { name: 'Search by username' }),
    'alex',
  )

  expect(within(table).getByText('student_alex')).toBeInTheDocument()
  expect(within(table).queryByText('nus_courier_99')).not.toBeInTheDocument()
})

test('search with no match shows empty state', async () => {
  const user = userEvent.setup()
  renderAdmin()
  await findTable()

  await user.type(
    screen.getByRole('searchbox', { name: 'Search by username' }),
    'zzz',
  )

  expect(screen.getByText('No users match your search.')).toBeInTheDocument()
})

test('promote and demote toggle a user between User and Admin', async () => {
  const user = userEvent.setup()
  renderAdmin()
  const table = await findTable()

  await user.click(
    rowFor(table, 'student_alex').getByRole('button', {
      name: 'Promote to Admin',
    }),
  )
  expect(
    await rowFor(table, 'student_alex').findByText('Admin'),
  ).toBeInTheDocument()

  await user.click(
    rowFor(table, 'student_alex').getByRole('button', {
      name: 'Demote to User',
    }),
  )
  expect(
    await rowFor(table, 'student_alex').findByText('User'),
  ).toBeInTheDocument()
})

test('remove asks for confirmation, then removes the user', async () => {
  const user = userEvent.setup()
  renderAdmin()
  const table = await findTable()

  await user.click(
    rowFor(table, 'utown_runner').getByRole('button', {
      name: 'Remove Account',
    }),
  )
  const dialog = screen.getByRole('dialog', { name: 'Remove Account' })
  await user.click(within(dialog).getByRole('button', { name: 'Remove' }))

  await waitFor(() =>
    expect(within(table).queryByText('utown_runner')).not.toBeInTheDocument(),
  )
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

test('cancelling removal keeps the user', async () => {
  const user = userEvent.setup()
  renderAdmin()
  const table = await findTable()

  await user.click(
    rowFor(table, 'utown_runner').getByRole('button', {
      name: 'Remove Account',
    }),
  )
  await user.click(screen.getByRole('button', { name: 'Cancel' }))

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(within(table).getByText('utown_runner')).toBeInTheDocument()
})

test('a failed role change shows the error and leaves the role unchanged', async () => {
  vi.spyOn(adminUserApi, 'changeRole').mockRejectedValue(
    new Error('Cannot demote the last admin.'),
  )
  const user = userEvent.setup()
  renderAdmin()
  const table = await findTable()

  await user.click(
    rowFor(table, 'nus_courier_99').getByRole('button', {
      name: 'Demote to User',
    }),
  )

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Cannot demote the last admin.',
  )
  expect(rowFor(table, 'nus_courier_99').getByText('Admin')).toBeInTheDocument()
})

test('a failed load shows an error', async () => {
  vi.spyOn(adminUserApi, 'listUsers').mockRejectedValue(
    new Error('Network down'),
  )
  renderAdmin()

  expect(await screen.findByRole('alert')).toHaveTextContent('Network down')
})
