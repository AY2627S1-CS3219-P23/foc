// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: tests for the Admin Dashboard page — Users section against the
// in-memory user/credit mocks, Suppliers section against a mocked
// supplier api module.
// Reviewed by: [pending]

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import {
  adminCreditApi,
  resetMockBalances,
} from '@/features/credit/adminCreditApi'
import * as supplierApi from '@/features/supplier/api'
import type { Supplier, Zone } from '@/features/supplier/types'
import { adminUserApi, resetMockUsers } from '@/features/user/adminApi'
import { routes } from '../routes'

vi.mock('@/features/supplier/api')

const zones: Zone[] = [{ code: 'COM', name: 'Computing' }]
const suppliers: Supplier[] = [
  {
    id: 's1',
    name: 'CoffeeBean@Com3',
    location: 'COM3-01-01',
    latitude: 1.29,
    longitude: 103.77,
    zoneCode: 'COM',
    categories: ['Food & Beverage'],
    openingTime: '08:00',
    closingTime: '18:00',
    description: '',
  },
]

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
function findSectionTable(name: 'Users' | 'Suppliers') {
  return section(name).findByRole('table')
}

function rowFor(table: HTMLElement, text: string) {
  const row = within(table).getByText(text).closest('tr')
  if (!row) throw new Error(`No row for ${text}`)
  return within(row)
}

beforeEach(() => {
  resetMockUsers()
  resetMockBalances()
  vi.restoreAllMocks()
  vi.mocked(supplierApi.listSuppliers).mockResolvedValue(suppliers)
  vi.mocked(supplierApi.listZones).mockResolvedValue(zones)
})

describe('Users section', () => {
  test('lists users with their roles and credits', async () => {
    renderAdmin()

    const table = await findSectionTable('Users')
    expect(
      screen.getByRole('heading', { name: 'Admin Dashboard' }),
    ).toBeInTheDocument()
    const alex = rowFor(table, 'student_alex')
    expect(alex.getByText('User')).toBeInTheDocument()
    expect(alex.getByText('15 / 3 reserved')).toBeInTheDocument()
    expect(
      rowFor(table, 'nus_courier_99').getByText('Admin'),
    ).toBeInTheDocument()
    expect(rowFor(table, 'foc_owner').getByText('Owner')).toBeInTheDocument()
  })

  test('owner can only receive credits', async () => {
    renderAdmin()

    const owner = rowFor(await findSectionTable('Users'), 'foc_owner')
    expect(owner.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Add Credits',
    ])
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

  test('add credits increases the available balance', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'student_alex').getByRole('button', {
        name: 'Add Credits',
      }),
    )
    const dialog = within(screen.getByRole('dialog', { name: 'Add Credits' }))
    await user.type(dialog.getByRole('spinbutton'), '10')
    await user.click(dialog.getByRole('button', { name: 'Add Credits' }))

    expect(
      await rowFor(table, 'student_alex').findByText('25 / 3 reserved'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('add credits rejects non-positive or fractional amounts', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'student_alex').getByRole('button', {
        name: 'Add Credits',
      }),
    )
    const dialog = within(screen.getByRole('dialog', { name: 'Add Credits' }))
    const submit = dialog.getByRole('button', { name: 'Add Credits' })

    await user.type(dialog.getByRole('spinbutton'), '0')
    expect(submit).toBeDisabled()
    expect(
      dialog.getByText('Enter a whole number greater than 0.'),
    ).toBeInTheDocument()

    await user.clear(dialog.getByRole('spinbutton'))
    await user.type(dialog.getByRole('spinbutton'), '2.5')
    expect(submit).toBeDisabled()
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

  test('users still show when credit balances fail to load', async () => {
    vi.spyOn(adminCreditApi, 'listBalances').mockRejectedValue(
      new Error('Credit service unavailable'),
    )
    renderAdmin()

    const table = await findSectionTable('Users')
    expect(rowFor(table, 'student_alex').getByText('—')).toBeInTheDocument()
    expect(section('Users').getByRole('alert')).toHaveTextContent(
      'Credit service unavailable',
    )
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
  test('lists suppliers with zone name and hours', async () => {
    renderAdmin()

    const row = rowFor(await findSectionTable('Suppliers'), 'CoffeeBean@Com3')
    expect(row.getByText('Food & Beverage')).toBeInTheDocument()
    expect(row.getByText('Computing')).toBeInTheDocument()
    expect(row.getByText('COM3-01-01')).toBeInTheDocument()
    expect(row.getByText('08:00–18:00')).toBeInTheDocument()
  })

  test('delete asks for confirmation, then removes the supplier', async () => {
    vi.mocked(supplierApi.deleteSupplier).mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Suppliers')

    await user.click(
      rowFor(table, 'CoffeeBean@Com3').getByRole('button', { name: 'Delete' }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Delete Supplier' }),
    )
    await user.click(dialog.getByRole('button', { name: 'Delete' }))

    expect(supplierApi.deleteSupplier).toHaveBeenCalledWith('s1')
    expect(
      await section('Suppliers').findByText('No suppliers yet.'),
    ).toBeInTheDocument()
  })

  test('a failed supplier load shows an error without affecting users', async () => {
    vi.mocked(supplierApi.listSuppliers).mockRejectedValue(new Error('boom'))
    renderAdmin()

    expect(await section('Suppliers').findByRole('alert')).toHaveTextContent(
      'Could not load suppliers. Try again.',
    )
    expect(await findSectionTable('Users')).toBeInTheDocument()
  })
})
