// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (Suppliers section tests replaced by a placeholder check;
// credit tests removed with the credit mock).
// Scope: tests for the Admin Dashboard page — Users section against the
// in-memory user mock, plus the Suppliers placeholder.
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

function section(name: 'Users' | 'Suppliers') {
  return within(screen.getByRole('region', { name }))
}

// Each section renders both a table (md+) and cards (mobile); jsdom
// applies no media queries, so scope queries to a section's table.
function findSectionTable(name: 'Users') {
  return section(name).findByRole('table')
}

function rowFor(table: HTMLElement, text: string) {
  const row = within(table).getByText(text).closest('tr')
  if (!row) throw new Error(`No row for ${text}`)
  return within(row)
}

beforeEach(() => {
  resetMockUsers()
  vi.restoreAllMocks()
})

describe('Users section', () => {
  test('lists users with their roles', async () => {
    renderAdmin()

    const table = await findSectionTable('Users')
    expect(
      screen.getByRole('heading', { name: 'Admin Dashboard' }),
    ).toBeInTheDocument()
    const alex = rowFor(table, 'student_alex')
    expect(alex.getByText('User')).toBeInTheDocument()
    expect(
      rowFor(table, 'nus_courier_99').getByText('Admin'),
    ).toBeInTheDocument()
    expect(rowFor(table, 'foc_owner').getByText('Owner')).toBeInTheDocument()
  })

  test('owner has no actions', async () => {
    renderAdmin()

    const owner = rowFor(await findSectionTable('Users'), 'foc_owner')
    expect(owner.queryAllByRole('button')).toEqual([])
  })

  test('search filters by username', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

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
    await findSectionTable('Users')

    await user.type(
      screen.getByRole('searchbox', { name: 'Search by username' }),
      'zzz',
    )

    expect(screen.getByText('No users match your search.')).toBeInTheDocument()
  })

  test('promote and demote toggle a user between User and Admin', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

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
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'utown_runner').getByRole('button', {
        name: 'Remove Account',
      }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Remove Account' }),
    )
    await user.click(dialog.getByRole('button', { name: 'Remove' }))

    await waitFor(() =>
      expect(within(table).queryByText('utown_runner')).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('cancelling removal keeps the user', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

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
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'nus_courier_99').getByRole('button', {
        name: 'Demote to User',
      }),
    )

    expect(await section('Users').findByRole('alert')).toHaveTextContent(
      'Cannot demote the last admin.',
    )
    expect(
      rowFor(table, 'nus_courier_99').getByText('Admin'),
    ).toBeInTheDocument()
  })

  test('a failed user load shows an error', async () => {
    vi.spyOn(adminUserApi, 'listUsers').mockRejectedValue(
      new Error('Network down'),
    )
    renderAdmin()

    expect(await section('Users').findByRole('alert')).toHaveTextContent(
      'Network down',
    )
  })
})

describe('Suppliers section', () => {
  test('shows a placeholder', async () => {
    renderAdmin()

    expect(
      await section('Suppliers').findByText(
        'Supplier management is coming soon.',
      ),
    ).toBeInTheDocument()
  })
})
