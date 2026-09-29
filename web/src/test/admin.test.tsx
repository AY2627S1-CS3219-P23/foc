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
// Scope: tests for the Admin Dashboard page — Users section, plus the
// Suppliers placeholder.
// Reviewed by: Ryan Ang

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { adminUserApi } from '@/features/user/adminApi'
import { USERS_PAGE_SIZE } from '@/features/user/components/UsersSection'
import type {
  AdminUser,
  AdminUserPage,
  ListUsersParams,
} from '@/features/user/types'
import { routes } from '../routes'

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
    await user.click(dialog.getByRole('button', { name: 'Remove' }))

    expect(await within(table).findByText(nth(1))).toBeInTheDocument()
    expect(lastListParams()).toEqual({ page: 0, size: USERS_PAGE_SIZE })
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

  test('an unknown signed-in admin gets a general warning when demoting', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    // the route guard's check succeeds; the Users section's own one fails
    vi.mocked(adminUserApi.getCurrentUser)
      .mockResolvedValueOnce({ ...seedUsers[2] })
      .mockRejectedValue(new Error('Unauthorized'))
    const user = userEvent.setup()
    renderAdmin()
    const table = await findSectionTable('Users')

    await user.click(
      rowFor(table, 'nus_courier_99').getByRole('button', {
        name: 'Demote to User',
      }),
    )

    expect(
      within(screen.getByRole('dialog', { name: 'Demote to User' })).getByText(
        /If this is your own account, you will lose access/,
      ),
    ).toBeInTheDocument()
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
  test('shows a placeholder', async () => {
    renderAdmin()

    expect(
      await (
        await findSection('Suppliers')
      ).findByText('Supplier management is coming soon.'),
    ).toBeInTheDocument()
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
