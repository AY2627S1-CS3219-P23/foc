// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (Suppliers section tests replaced by a placeholder check;
// credit tests removed with the credit mock; Users section tested
// against a fake adminUserApi, with search, role filter and paging).
// 2026-09-29 (second PR #140 review): cases for a role change emptying a
// later page, the general warning when the signed-in admin is unknown, the
// error banner clearing on a new query, and whitespace-only search edits.
// 2026-09-29 (issue #147): the fake soft-deletes like user-service, and
// cases for the "Show removed accounts" toggle (team decision).
// 2026-09-29 (issue #147): cases for the admin-only route guard.
// Nav-bar and tab-bar Admin Dashboard link shown to ADMIN/OWNER only.
// Bug fixes: one GET /users per action, no fetch of an emptied page, and
// actions off while the list reloads. PR #152 review: one GET /users/me per page (the
// unknown-admin fallback case can no longer happen, so its test went). The Suppliers section's read-only
// list (team decision) against a faked listSuppliers; 2026-09-30: its
// Add / Edit / Delete and "Try again" against the faked supplier API.
// PR #156 review: failed saves shown in the form, failed deletes closing
// the confirm, retry after a failed save, no other page's rows after a
// failed page load, and the success line clearing on a page change.
// PR #156 re-review: a failed delete's fallback naming the supplier, and a
// deleted row staying gone when the reload after it fails.
// Scope: tests for the Admin Dashboard page — Users and Suppliers
// sections, the route guard and the nav link.
// Reviewed by: Ryan Ang

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  updateSupplier,
} from '@/features/supplier/api'
import { ADMIN_SUPPLIERS_PAGE_SIZE } from '@/features/supplier/components/SuppliersAdminSection'
import type { PagedResponse, Supplier } from '@/features/supplier/types'
import { adminUserApi } from '@/features/user/adminApi'
import { USERS_PAGE_SIZE } from '@/features/user/components/UsersSection'
import type {
  AdminUser,
  AdminUserPage,
  ListUsersParams,
} from '@/features/user/types'
import { routes } from '../routes'

// the Suppliers section's calls; faked per test
vi.mock('@/features/supplier/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/supplier/api')>()),
  listSuppliers: vi.fn(),
  createSupplier: vi.fn(),
  updateSupplier: vi.fn(),
  deleteSupplier: vi.fn(),
}))

const seedSuppliers: Supplier[] = [
  {
    id: 's1',
    name: 'CoffeeBean@Com3',
    location: 'COM3-01-01',
    latitude: 1.29,
    longitude: 103.77,
    categories: ['Food', 'Beverage'],
    openingTime: '08:00',
    closingTime: '18:00',
    description: '',
  },
  {
    id: 's2',
    name: 'Fine Foods UTown',
    location: 'Plaza Level 1',
    latitude: 1.3,
    longitude: 103.77,
    categories: ['Food'],
    openingTime: '07:00',
    closingTime: '22:00',
    description: '',
  },
]

function supplierPage(
  content: Supplier[],
  page = 0,
  totalPages = content.length ? 1 : 0,
): PagedResponse<Supplier> {
  return {
    content,
    page,
    size: ADMIN_SUPPLIERS_PAGE_SIZE,
    totalElements: content.length,
    totalPages,
  }
}

const seedUsers: readonly AdminUser[] = [
  {
    id: 1,
    email: 'e0000001@u.nus.edu',
    username: 'foc_owner',
    role: 'OWNER',
    createdAt: '2026-09-01T09:00:00Z',
  },
  {
    id: 2,
    email: 'e0123456@u.nus.edu',
    username: 'student_alex',
    role: 'USER',
    createdAt: '2026-09-10T03:15:00Z',
  },
  {
    id: 3,
    email: 'e0999999@u.nus.edu',
    username: 'nus_courier_99',
    role: 'ADMIN',
    createdAt: '2026-09-12T11:40:00Z',
  },
  {
    id: 4,
    email: 'e0345678@u.nus.edu',
    username: 'utown_runner',
    role: 'USER',
    createdAt: '2026-09-18T07:05:00Z',
  },
]

let users: AdminUser[] = []

// Stand-in for GET /users: filters, then pages like the backend.
function fakeListUsers(params: ListUsersParams): AdminUserPage {
  const search = params.search?.toLowerCase()
  const matches = users.filter(
    (u) =>
      (params.includeDeleted || !u.deletedAt) &&
      (!params.role || u.role === params.role) &&
      (!search ||
        String(u.id).includes(search) ||
        u.username.toLowerCase().includes(search) ||
        u.email.toLowerCase().includes(search)),
  )
  const start = params.page * params.size
  return {
    content: matches.slice(start, start + params.size),
    page: {
      size: params.size,
      number: params.page,
      totalElements: matches.length,
      totalPages: Math.ceil(matches.length / params.size),
    },
  }
}

// Username of the n-th (1-based) user from manyUsers.
function nth(n: number) {
  return `user_${String(n).padStart(3, '0')}`
}

function manyUsers(count: number): AdminUser[] {
  return Array.from({ length: count }, (_, i) => ({
    id: 1000 + i,
    email: `e${1000000 + i}@u.nus.edu`,
    username: nth(i + 1),
    role: 'USER',
    createdAt: '2026-09-20T00:00:00Z',
  }))
}

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

// The page appears once the admin route guard's role check resolves, so
// the first query of a test waits for its section.
async function findSection(name: 'Users' | 'Suppliers') {
  return within(await screen.findByRole('region', { name }))
}

// Each section renders both a table (md+) and cards (mobile); jsdom
// applies no media queries, so scope queries to a section's table.
async function findSectionTable(name: 'Users') {
  return (await findSection(name)).findByRole('table')
}

function rowFor(table: HTMLElement, text: string) {
  const row = within(table).getByText(text).closest('tr')
  if (!row) throw new Error(`No row for ${text}`)
  return within(row)
}

afterEach(() => {
  localStorage.clear()
})

// Clicks a row's Promote/Demote button, then confirms in the dialog.
async function changeRole(
  user: ReturnType<typeof userEvent.setup>,
  table: HTMLElement,
  username: string,
  action: 'Promote to Admin' | 'Demote to User',
) {
  await user.click(
    rowFor(table, username).getByRole('button', { name: action }),
  )
  const dialog = within(screen.getByRole('dialog', { name: action }))
  await user.click(
    dialog.getByRole('button', {
      name: action === 'Promote to Admin' ? 'Promote' : 'Demote',
    }),
  )
}

function lastListParams() {
  return vi.mocked(adminUserApi.listUsers).mock.lastCall?.[0]
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.mocked(listSuppliers)
    .mockReset()
    .mockResolvedValue(supplierPage(seedSuppliers))
  vi.mocked(createSupplier)
    .mockReset()
    .mockImplementation(async (input) => ({ id: 'new', ...input }))
  vi.mocked(updateSupplier)
    .mockReset()
    .mockImplementation(async (id, input) => ({ id, ...input }))
  vi.mocked(deleteSupplier).mockReset().mockResolvedValue(undefined)
  // /admin sits behind ProtectedRoute, which only checks that
  // AuthProvider found a stored session under "user".
  localStorage.setItem('user', JSON.stringify({ sub: 'test-admin' }))
  users = seedUsers.map((u) => ({ ...u }))
  vi.spyOn(adminUserApi, 'listUsers').mockImplementation(async (params) =>
    fakeListUsers(params),
  )
  vi.spyOn(adminUserApi, 'changeRole').mockImplementation(async (id, role) => {
    const user = users.find((u) => u.id === id)
    if (!user) throw new Error('User not found.')
    user.role = role
    return { ...user }
  })
  // soft delete, like user-service
  vi.spyOn(adminUserApi, 'removeUser').mockImplementation(async (id) => {
    users = users.map((u) =>
      u.id === id ? { ...u, deletedAt: '2026-09-29T08:00:00Z' } : u,
    )
  })
  // Signed in as nus_courier_99 (an ADMIN in the seed data).
  vi.spyOn(adminUserApi, 'getCurrentUser').mockResolvedValue({
    ...seedUsers[2],
  })
})

describe('Users section', () => {
  test('lists users with their roles', async () => {
    renderAdmin()

    const table = await findSectionTable('Users')
    expect(
      screen.getByRole('heading', { name: 'Admin Dashboard' }),
    ).toBeInTheDocument()
    expect(rowFor(table, 'student_alex').getByText('User')).toBeInTheDocument()
    expect(rowFor(table, 'student_alex').getByText('2')).toBeInTheDocument()
    expect(
      rowFor(table, 'nus_courier_99').getByText('Admin'),
    ).toBeInTheDocument()
    expect(rowFor(table, 'foc_owner').getByText('Owner')).toBeInTheDocument()
    expect(adminUserApi.listUsers).toHaveBeenCalledWith({
      page: 0,
      size: USERS_PAGE_SIZE,
    })
  })

  test('owner has no actions', async () => {
    renderAdmin()

    const owner = rowFor(await findSectionTable('Users'), 'foc_owner')
    expect(owner.queryAllByRole('button')).toEqual([])
  })

  test('search is sent to the server once typing pauses', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.type(
      screen.getByRole('searchbox', {
        name: 'Search by ID, username or email',
      }),
      'alex',
    )

    await waitFor(() =>
      expect(
        within(table).queryByText('nus_courier_99'),
      ).not.toBeInTheDocument(),
    )
    expect(within(table).getByText('student_alex')).toBeInTheDocument()
    // One request for the initial load, one for the finished word.
    expect(adminUserApi.listUsers).toHaveBeenCalledTimes(2)
    expect(lastListParams()).toEqual({
      search: 'alex',
      page: 0,
      size: USERS_PAGE_SIZE,
    })
  })

  test('role filter is sent to the server', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'ADMIN',
    )

    await waitFor(() =>
      expect(within(table).queryByText('student_alex')).not.toBeInTheDocument(),
    )
    expect(within(table).getByText('nus_courier_99')).toBeInTheDocument()
    expect(lastListParams()).toEqual({
      role: 'ADMIN',
      page: 0,
      size: USERS_PAGE_SIZE,
    })
  })

  test('next, previous and page numbers request that page', async () => {
    users = manyUsers(USERS_PAGE_SIZE * 2 + 5)
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    const pager = within(section('Users').getByRole('navigation'))

    expect(within(table).getByText(nth(1))).toBeInTheDocument()
    expect(pager.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(pager.getByRole('button', { name: 'Page 1' })).toHaveAttribute(
      'aria-current',
      'page',
    )

    await user.click(pager.getByRole('button', { name: 'Next' }))
    expect(
      await within(table).findByText(nth(USERS_PAGE_SIZE + 1)),
    ).toBeInTheDocument()
    expect(lastListParams()).toEqual({ page: 1, size: USERS_PAGE_SIZE })

    await user.click(pager.getByRole('button', { name: 'Page 3' }))
    expect(
      await within(table).findByText(nth(USERS_PAGE_SIZE * 2 + 1)),
    ).toBeInTheDocument()
    expect(pager.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.click(pager.getByRole('button', { name: 'Previous' }))
    expect(
      await within(table).findByText(nth(USERS_PAGE_SIZE + 1)),
    ).toBeInTheDocument()
    expect(lastListParams()).toEqual({ page: 1, size: USERS_PAGE_SIZE })
  })

  test('changing the search or role filter goes back to page 1', async () => {
    users = [
      ...manyUsers(USERS_PAGE_SIZE * 2 + 5),
      ...seedUsers.map((u) => ({ ...u })),
    ]
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    const pager = within(section('Users').getByRole('navigation'))

    await user.click(pager.getByRole('button', { name: 'Page 2' }))
    expect(
      await within(table).findByText(nth(USERS_PAGE_SIZE + 1)),
    ).toBeInTheDocument()

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'USER',
    )
    await waitFor(() =>
      expect(lastListParams()).toEqual({
        role: 'USER',
        page: 0,
        size: USERS_PAGE_SIZE,
      }),
    )

    await user.click(
      await within(section('Users').getByRole('navigation')).findByRole(
        'button',
        { name: 'Page 2' },
      ),
    )
    await waitFor(() =>
      expect(lastListParams()).toEqual({
        role: 'USER',
        page: 1,
        size: USERS_PAGE_SIZE,
      }),
    )

    await user.type(
      screen.getByRole('searchbox', {
        name: 'Search by ID, username or email',
      }),
      'user_0',
    )
    await waitFor(() =>
      expect(lastListParams()).toEqual({
        search: 'user_0',
        role: 'USER',
        page: 0,
        size: USERS_PAGE_SIZE,
      }),
    )
  })

  test('search with no match shows empty state', async () => {
    const user = userEvent.setup()
    renderAdmin()
    await findSectionTable('Users')

    await user.type(
      screen.getByRole('searchbox', {
        name: 'Search by ID, username or email',
      }),
      'zzz',
    )

    expect(
      await screen.findByText('No users match your search or filter.'),
    ).toBeInTheDocument()
  })

  test('promote and demote toggle a user between User and Admin', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await changeRole(user, table, 'student_alex', 'Promote to Admin')
    expect(
      await rowFor(table, 'student_alex').findByText('Admin'),
    ).toBeInTheDocument()
    expect(adminUserApi.changeRole).toHaveBeenCalledWith(2, 'ADMIN')

    await changeRole(user, table, 'student_alex', 'Demote to User')
    expect(
      await rowFor(table, 'student_alex').findByText('User'),
    ).toBeInTheDocument()
    expect(adminUserApi.changeRole).toHaveBeenLastCalledWith(2, 'USER')
  })

  test('removed accounts are hidden until "Show removed accounts" is ticked', async () => {
    users.push({
      id: 99,
      email: 'e0999000@u.nus.edu',
      username: 'left_already',
      role: 'USER',
      createdAt: '2026-09-01T00:00:00Z',
      deletedAt: '2026-09-20T08:00:00Z',
    })
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    expect(within(table).queryByText('left_already')).not.toBeInTheDocument()

    await user.click(
      screen.getByRole('checkbox', { name: 'Show removed accounts' }),
    )

    await waitFor(() => expect(lastListParams()?.includeDeleted).toBe(true))
    const row = rowFor(await findSectionTable('Users'), 'left_already')
    expect(row.getByText('Removed 20 September 2026')).toBeInTheDocument()
    // view only: no actions on a removed account
    expect(row.queryByRole('button')).not.toBeInTheDocument()
  })

  test('removing a user while removed accounts are shown greys the row', async () => {
    const user = userEvent.setup()
    renderAdmin()
    await findSectionTable('Users')
    await user.click(
      screen.getByRole('checkbox', { name: 'Show removed accounts' }),
    )
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'utown_runner').getByRole('button', {
        name: 'Remove Account',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Remove Account' })).getByRole(
        'button',
        { name: 'Remove' },
      ),
    )

    await waitFor(() =>
      expect(
        rowFor(table, 'utown_runner').getByText('Removed 29 September 2026'),
      ).toBeInTheDocument(),
    )
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
    expect(adminUserApi.removeUser).toHaveBeenCalledWith(4)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('cancelling a role change keeps the role', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'student_alex').getByRole('button', {
        name: 'Promote to Admin',
      }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Promote to Admin' }),
    )
    expect(dialog.getByText('student_alex')).toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(rowFor(table, 'student_alex').getByText('User')).toBeInTheDocument()
    expect(adminUserApi.changeRole).not.toHaveBeenCalled()
  })

  test('demoting yourself warns about losing admin access', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    // Wait until the page knows who is signed in.
    await waitFor(() =>
      expect(
        rowFor(table, 'nus_courier_99').queryByRole('button', {
          name: 'Remove Account',
        }),
      ).not.toBeInTheDocument(),
    )

    await user.click(
      rowFor(table, 'nus_courier_99').getByRole('button', {
        name: 'Demote to User',
      }),
    )

    expect(
      within(screen.getByRole('dialog', { name: 'Demote to User' })).getByText(
        /You will lose access to the admin dashboard/,
      ),
    ).toBeInTheDocument()
  })

  test('the signed-in admin has no Remove on their own row', async () => {
    renderAdmin()
    const table = await findSectionTable('Users')

    await waitFor(() =>
      expect(
        rowFor(table, 'nus_courier_99').queryByRole('button', {
          name: 'Remove Account',
        }),
      ).not.toBeInTheDocument(),
    )
    expect(
      rowFor(table, 'nus_courier_99').getByRole('button', {
        name: 'Demote to User',
      }),
    ).toBeInTheDocument()
    expect(
      rowFor(table, 'student_alex').getByRole('button', {
        name: 'Remove Account',
      }),
    ).toBeInTheDocument()
  })

  test('a role change reloads the list, so the filter still applies', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'USER',
    )
    await waitFor(() =>
      expect(
        within(table).queryByText('nus_courier_99'),
      ).not.toBeInTheDocument(),
    )
    await changeRole(user, table, 'student_alex', 'Promote to Admin')

    await waitFor(() =>
      expect(within(table).queryByText('student_alex')).not.toBeInTheDocument(),
    )
    expect(within(table).getByText('utown_runner')).toBeInTheDocument()
  })

  test('removing the last user on a page goes back a page', async () => {
    users = manyUsers(USERS_PAGE_SIZE + 1)
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      within(section('Users').getByRole('navigation')).getByRole('button', {
        name: 'Page 2',
      }),
    )
    const last = nth(USERS_PAGE_SIZE + 1)
    await within(table).findByText(last)
    await user.click(
      rowFor(table, last).getByRole('button', { name: 'Remove Account' }),
    )
    const dialog = within(
      screen.getByRole('dialog', { name: 'Remove Account' }),
    )
    const callsBefore = vi.mocked(adminUserApi.listUsers).mock.calls.length
    await user.click(dialog.getByRole('button', { name: 'Remove' }))

    expect(await within(table).findByText(nth(1))).toBeInTheDocument()
    expect(lastListParams()).toEqual({ page: 0, size: USERS_PAGE_SIZE })
    // straight to page 1: the emptied page 2 is never fetched
    const after = vi
      .mocked(adminUserApi.listUsers)
      .mock.calls.slice(callsBefore)
    expect(after.map(([params]) => params.page)).toEqual([0])
  })

  test('a role change fetches the list once and shows the new role', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    const callsBefore = vi.mocked(adminUserApi.listUsers).mock.calls.length

    await changeRole(user, table, 'student_alex', 'Promote to Admin')

    await waitFor(() =>
      expect(
        rowFor(table, 'student_alex').getByRole('button', {
          name: 'Demote to User',
        }),
      ).toBeInTheDocument(),
    )
    expect(vi.mocked(adminUserApi.listUsers).mock.calls.length).toBe(
      callsBefore + 1,
    )
  })

  test('actions and confirm are off while the list reloads', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')
    await user.click(
      rowFor(table, 'utown_runner').getByRole('button', {
        name: 'Remove Account',
      }),
    )
    // a reload that hasn't answered yet
    vi.mocked(adminUserApi.listUsers).mockReturnValue(new Promise(() => {}))
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'USER',
    )

    const dialog = within(
      screen.getByRole('dialog', { name: 'Remove Account' }),
    )
    expect(dialog.getByRole('button', { name: 'Remove' })).toBeDisabled()
    expect(
      rowFor(table, 'student_alex').getByRole('button', {
        name: 'Promote to Admin',
      }),
    ).toBeDisabled()
  })

  test('a role change that empties a later page goes back a page', async () => {
    users = manyUsers(USERS_PAGE_SIZE + 1)
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'USER',
    )
    await user.click(
      await within(section('Users').getByRole('navigation')).findByRole(
        'button',
        { name: 'Page 2' },
      ),
    )
    const last = nth(USERS_PAGE_SIZE + 1)
    await within(table).findByText(last)
    await changeRole(user, table, last, 'Promote to Admin')

    expect(await within(table).findByText(nth(1))).toBeInTheDocument()
    expect(lastListParams()).toEqual({
      role: 'USER',
      page: 0,
      size: USERS_PAGE_SIZE,
    })
  })

  test('the page asks GET /users/me once, shared by the guard and the list', async () => {
    renderAdmin()
    const table = await findSectionTable('Users')

    // the signed-in admin's own row still has no Remove
    expect(
      rowFor(table, 'nus_courier_99').queryByRole('button', {
        name: 'Remove Account',
      }),
    ).not.toBeInTheDocument()
    expect(adminUserApi.getCurrentUser).toHaveBeenCalledTimes(1)
  })

  test('a new search or filter clears the last action error', async () => {
    vi.mocked(adminUserApi.changeRole).mockRejectedValue(
      new Error('You cannot change this user.'),
    )
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await changeRole(user, table, 'student_alex', 'Promote to Admin')
    expect(
      await (await findSection('Users')).findByRole('alert'),
    ).toHaveTextContent('You cannot change this user.')

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Filter by role' }),
      'USER',
    )
    await waitFor(() =>
      expect(section('Users').queryByRole('alert')).not.toBeInTheDocument(),
    )
  })

  test("a whitespace-only search edit doesn't reload or reset the page", async () => {
    users = manyUsers(USERS_PAGE_SIZE * 2 + 5)
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      within(section('Users').getByRole('navigation')).getByRole('button', {
        name: 'Page 2',
      }),
    )
    await within(table).findByText(nth(USERS_PAGE_SIZE + 1))
    const calls = vi.mocked(adminUserApi.listUsers).mock.calls.length

    await user.type(
      screen.getByRole('searchbox', {
        name: 'Search by ID, username or email',
      }),
      '  ',
    )
    // longer than the debounce
    await new Promise((resolve) => setTimeout(resolve, 400))

    expect(vi.mocked(adminUserApi.listUsers).mock.calls.length).toBe(calls)
    expect(
      within(table).getByText(nth(USERS_PAGE_SIZE + 1)),
    ).toBeInTheDocument()
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
    expect(adminUserApi.removeUser).not.toHaveBeenCalled()
  })

  test('a failed role change shows the error and leaves the role unchanged', async () => {
    vi.mocked(adminUserApi.changeRole).mockRejectedValue(
      new Error('You cannot change this user.'),
    )
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await changeRole(user, table, 'nus_courier_99', 'Demote to User')

    expect(
      await (await findSection('Users')).findByRole('alert'),
    ).toHaveTextContent('You cannot change this user.')
    expect(
      rowFor(table, 'nus_courier_99').getByText('Admin'),
    ).toBeInTheDocument()
  })

  test('a failed user load shows an error', async () => {
    vi.mocked(adminUserApi.listUsers).mockRejectedValue(
      new Error('Network down'),
    )
    renderAdmin()

    expect(
      await (await findSection('Users')).findByRole('alert'),
    ).toHaveTextContent('Network down')
  })
})

describe('Suppliers section', () => {
  test('lists suppliers with category, location and hours', async () => {
    renderAdmin()

    const table = await (await findSection('Suppliers')).findByRole('table')
    const row = rowFor(table, 'CoffeeBean@Com3')
    expect(row.getByText('Food, Beverage')).toBeInTheDocument()
    expect(row.getByText('COM3-01-01')).toBeInTheDocument()
    expect(row.getByText('08:00–18:00')).toBeInTheDocument()
    expect(within(table).getByText('Fine Foods UTown')).toBeInTheDocument()
    expect(listSuppliers).toHaveBeenCalledWith({
      page: 0,
      size: ADMIN_SUPPLIERS_PAGE_SIZE,
    })
  })

  test('adds a supplier through the form', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    await suppliers.findByRole('table')
    const loadsBefore = vi.mocked(listSuppliers).mock.calls.length

    await user.click(suppliers.getByRole('button', { name: '+ Add Supplier' }))
    const form = within(screen.getByRole('dialog', { name: 'Add Supplier' }))
    await user.type(form.getByLabelText(/Supplier Name/), 'Techno Cafe')
    await user.type(form.getByLabelText(/Location/), 'E3-01')
    await user.type(form.getByLabelText(/Latitude/), '1.2998')
    await user.type(form.getByLabelText(/Longitude/), '103.7713')
    await user.click(form.getByRole('button', { name: 'Save Supplier' }))

    expect(await suppliers.findByRole('status')).toHaveTextContent(
      'Successfully created Supplier "Techno Cafe".',
    )
    expect(createSupplier).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Techno Cafe',
        location: 'E3-01',
        latitude: 1.2998,
        longitude: 103.7713,
      }),
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    // the list reloads to show it
    await waitFor(() =>
      expect(vi.mocked(listSuppliers).mock.calls.length).toBe(loadsBefore + 1),
    )
  })

  test('the supplier form closes only with Cancel or ✕, not a click outside', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    await suppliers.findByRole('table')
    const openForm = async () => {
      await user.click(
        suppliers.getByRole('button', { name: '+ Add Supplier' }),
      )
      return screen.getByRole('dialog', { name: 'Add Supplier' })
    }

    const dialog = await openForm()
    await user.type(
      within(dialog).getByLabelText(/Supplier Name/),
      'Half-filled',
    )
    // the darkened backdrop around the form
    await user.click(dialog.parentElement as HTMLElement)
    expect(
      screen.getByRole('dialog', { name: 'Add Supplier' }),
    ).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/Supplier Name/)).toHaveValue(
      'Half-filled',
    )

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const again = await openForm()
    await user.click(within(again).getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('the delete confirmation still closes on a click outside', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const table = await (await findSection('Suppliers')).findByRole('table')

    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    const dialog = screen.getByRole('dialog', { name: 'Delete Supplier' })
    await user.click(dialog.parentElement as HTMLElement)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(deleteSupplier).not.toHaveBeenCalled()
  })

  test('edits a supplier, starting from its current details', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    await user.click(
      rowFor(table, 'CoffeeBean@Com3').getByRole('button', {
        name: 'Edit CoffeeBean@Com3',
      }),
    )
    const form = within(screen.getByRole('dialog', { name: 'Edit Supplier' }))
    const name = form.getByLabelText(/Supplier Name/)
    expect(name).toHaveValue('CoffeeBean@Com3')
    await user.clear(name)
    await user.type(name, 'CoffeeBean Com3')
    await user.click(form.getByRole('button', { name: 'Save Supplier' }))

    expect(await suppliers.findByRole('status')).toHaveTextContent(
      'Successfully updated Supplier "CoffeeBean Com3".',
    )
    expect(updateSupplier).toHaveBeenCalledWith(
      's1',
      expect.objectContaining({
        name: 'CoffeeBean Com3',
        location: 'COM3-01-01',
      }),
    )
  })

  test('deletes a supplier after confirming', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    )

    expect(await suppliers.findByRole('status')).toHaveTextContent(
      'Successfully deleted Supplier "Fine Foods UTown".',
    )
    expect(deleteSupplier).toHaveBeenCalledWith('s2')
  })

  test('shows the reason when a delete fails', async () => {
    vi.mocked(deleteSupplier).mockRejectedValue(new Error('Supplier not found'))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    )

    expect(await suppliers.findByRole('alert')).toHaveTextContent(
      'Supplier not found',
    )
    expect(suppliers.queryByRole('status')).not.toBeInTheDocument()
    // the confirm closes, or it would cover the alert
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('a failed delete with no reason from the server names the supplier', async () => {
    vi.mocked(deleteSupplier).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    )

    // the confirm is gone by now, so the alert has to say which one
    expect(await suppliers.findByRole('alert')).toHaveTextContent(
      'Could not delete supplier "Fine Foods UTown".',
    )
  })

  test('a deleted row stays gone when the reload after it fails', async () => {
    vi.mocked(listSuppliers)
      .mockResolvedValueOnce(supplierPage(seedSuppliers))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    )

    await suppliers.findByRole('button', { name: 'Try again' })
    expect(suppliers.queryByText('Fine Foods UTown')).not.toBeInTheDocument()
    expect(within(table).getByText('CoffeeBean@Com3')).toBeInTheDocument()
  })

  test('a failed save shows its reason inside the form, which stays open', async () => {
    vi.mocked(createSupplier).mockRejectedValue(
      new Error('Supplier name already exists'),
    )
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    await suppliers.findByRole('table')

    await user.click(suppliers.getByRole('button', { name: '+ Add Supplier' }))
    const dialog = screen.getByRole('dialog', { name: 'Add Supplier' })
    const form = within(dialog)
    await user.type(form.getByLabelText(/Supplier Name/), 'CoffeeBean@Com3')
    await user.type(form.getByLabelText(/Location/), 'COM3-01-01')
    await user.type(form.getByLabelText(/Latitude/), '1.29')
    await user.type(form.getByLabelText(/Longitude/), '103.77')
    await user.click(form.getByRole('button', { name: 'Save Supplier' }))

    expect(await form.findByRole('alert')).toHaveTextContent(
      'Supplier name already exists',
    )
    expect(form.getByLabelText(/Supplier Name/)).toHaveValue('CoffeeBean@Com3')
    // only there, not also behind the form
    expect(screen.getAllByRole('alert')).toHaveLength(1)

    // reopening starts without the old error
    await user.click(form.getByRole('button', { name: 'Cancel' }))
    await user.click(suppliers.getByRole('button', { name: '+ Add Supplier' }))
    expect(
      within(screen.getByRole('dialog', { name: 'Add Supplier' })).queryByRole(
        'alert',
      ),
    ).not.toBeInTheDocument()
  })

  test('"Try again" stays after a failed load, even once a save fails', async () => {
    vi.mocked(listSuppliers)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(supplierPage(seedSuppliers))
    vi.mocked(createSupplier).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    await suppliers.findByRole('button', { name: 'Try again' })

    await user.click(suppliers.getByRole('button', { name: '+ Add Supplier' }))
    const form = within(screen.getByRole('dialog', { name: 'Add Supplier' }))
    await user.type(form.getByLabelText(/Supplier Name/), 'Techno Cafe')
    await user.type(form.getByLabelText(/Location/), 'E3-01')
    await user.type(form.getByLabelText(/Latitude/), '1.2998')
    await user.type(form.getByLabelText(/Longitude/), '103.7713')
    await user.click(form.getByRole('button', { name: 'Save Supplier' }))
    await form.findByRole('alert')
    await user.click(form.getByRole('button', { name: 'Cancel' }))

    await user.click(suppliers.getByRole('button', { name: 'Try again' }))
    expect(await suppliers.findByRole('table')).toBeInTheDocument()
  })

  test('a failed page load hides the other page\'s rows and offers "Try again"', async () => {
    vi.mocked(listSuppliers).mockImplementation(async (params = {}) => {
      if (params.page === 1) throw new TypeError('Failed to fetch')
      return supplierPage([seedSuppliers[0]], 0, 2)
    })
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')
    await within(table).findByText('CoffeeBean@Com3')

    await user.click(
      within(suppliers.getByRole('navigation')).getByRole('button', {
        name: 'Page 2',
      }),
    )

    expect(
      await suppliers.findByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument()
    expect(suppliers.queryByText('CoffeeBean@Com3')).not.toBeInTheDocument()
    expect(
      suppliers.queryByRole('button', { name: 'Edit CoffeeBean@Com3' }),
    ).not.toBeInTheDocument()
  })

  test('a failed reload keeps the rows but turns their actions off', async () => {
    vi.mocked(listSuppliers)
      .mockResolvedValueOnce(supplierPage(seedSuppliers))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')

    // the delete reloads the list, and that reload fails
    await user.click(
      rowFor(table, 'Fine Foods UTown').getByRole('button', {
        name: 'Delete Fine Foods UTown',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
        'button',
        { name: 'Delete' },
      ),
    )

    expect(
      await suppliers.findByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument()
    expect(
      rowFor(table, 'CoffeeBean@Com3').getByRole('button', {
        name: 'Edit CoffeeBean@Com3',
      }),
    ).toBeDisabled()
  })

  test('the success line clears on a page change', async () => {
    vi.mocked(listSuppliers).mockImplementation(async (params = {}) =>
      supplierPage(
        params.page === 1 ? [seedSuppliers[1]] : [seedSuppliers[0]],
        params.page,
        2,
      ),
    )
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')
    await within(table).findByText('CoffeeBean@Com3')

    await user.click(
      rowFor(table, 'CoffeeBean@Com3').getByRole('button', {
        name: 'Edit CoffeeBean@Com3',
      }),
    )
    await user.click(
      within(screen.getByRole('dialog', { name: 'Edit Supplier' })).getByRole(
        'button',
        { name: 'Save Supplier' },
      ),
    )
    await suppliers.findByRole('status')

    await user.click(
      within(suppliers.getByRole('navigation')).getByRole('button', {
        name: 'Page 2',
      }),
    )
    expect(suppliers.queryByRole('status')).not.toBeInTheDocument()
  })

  test('"Try again" reloads after a failed load', async () => {
    vi.mocked(listSuppliers)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(supplierPage(seedSuppliers))
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')

    await user.click(
      within(await suppliers.findByRole('alert')).getByRole('button', {
        name: 'Try again',
      }),
    )

    const table = await suppliers.findByRole('table')
    expect(within(table).getByText('CoffeeBean@Com3')).toBeInTheDocument()
    expect(suppliers.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('pages through suppliers', async () => {
    vi.mocked(listSuppliers).mockImplementation(async (params = {}) =>
      supplierPage(
        params.page === 1 ? [seedSuppliers[1]] : [seedSuppliers[0]],
        params.page,
        2,
      ),
    )
    const user = userEvent.setup()
    renderAdmin()
    const suppliers = await findSection('Suppliers')
    const table = await suppliers.findByRole('table')
    await within(table).findByText('CoffeeBean@Com3')

    await user.click(
      within(suppliers.getByRole('navigation')).getByRole('button', {
        name: 'Page 2',
      }),
    )

    expect(
      await within(table).findByText('Fine Foods UTown'),
    ).toBeInTheDocument()
    expect(vi.mocked(listSuppliers).mock.lastCall?.[0]).toEqual({
      page: 1,
      size: ADMIN_SUPPLIERS_PAGE_SIZE,
    })
  })

  test('shows an empty state', async () => {
    vi.mocked(listSuppliers).mockResolvedValue(supplierPage([]))
    renderAdmin()

    expect(
      await (await findSection('Suppliers')).findByText('No suppliers yet.'),
    ).toBeInTheDocument()
  })

  test('shows an error when suppliers fail to load', async () => {
    vi.mocked(listSuppliers).mockRejectedValue(new TypeError('Failed to fetch'))
    renderAdmin()

    expect(
      await (await findSection('Suppliers')).findByRole('alert'),
    ).toHaveTextContent('Could not load suppliers.')
  })
})

describe('Admin route guard', () => {
  test('a USER is sent to the home page', async () => {
    vi.mocked(adminUserApi.getCurrentUser).mockResolvedValue({
      ...seedUsers[1],
    })
    renderAdmin()

    expect(
      await screen.findByRole('heading', { name: 'Welcome Back!' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Admin Dashboard' }),
    ).not.toBeInTheDocument()
    expect(adminUserApi.listUsers).not.toHaveBeenCalled()
  })

  test('an OWNER sees the dashboard', async () => {
    vi.mocked(adminUserApi.getCurrentUser).mockResolvedValue({
      ...seedUsers[0],
    })
    renderAdmin()

    expect(
      await screen.findByRole('heading', { name: 'Admin Dashboard' }),
    ).toBeInTheDocument()
  })

  test('shows a message when the role check fails', async () => {
    vi.mocked(adminUserApi.getCurrentUser).mockRejectedValue(
      new TypeError('Failed to fetch'),
    )
    renderAdmin()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not check your access. Try again.',
    )
    expect(
      screen.queryByRole('heading', { name: 'Admin Dashboard' }),
    ).not.toBeInTheDocument()
  })
})

describe('Admin Dashboard nav link', () => {
  test('an ADMIN sees it in the nav bar and the tab bar', async () => {
    renderAdmin()
    await findSectionTable('Users')

    const nav = within(screen.getByRole('navigation', { name: 'Main' }))
    expect(nav.getByRole('link', { name: 'Admin Dashboard' })).toHaveAttribute(
      'href',
      '/admin',
    )
    const tabs = within(screen.getByRole('navigation', { name: 'Main tabs' }))
    expect(tabs.getByRole('link', { name: 'Admin' })).toHaveAttribute(
      'href',
      '/admin',
    )
  })

  test('a USER does not see it', async () => {
    vi.mocked(adminUserApi.getCurrentUser).mockResolvedValue({
      ...seedUsers[1],
    })
    renderAdmin()
    await screen.findByRole('heading', { name: 'Welcome Back!' })

    expect(
      screen.queryByRole('link', { name: 'Admin Dashboard' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: 'Admin' }),
    ).not.toBeInTheDocument()
  })
})
