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
// list (team decision) against a faked listSuppliers.
// 2026-09-30 (Claude Code, Opus 5.5): the Suppliers section's cases and
// its faked supplier API removed with the section, and a case that the
// dashboard shows only the Users section for now.
// Scope: tests for the Admin Dashboard page — the Users section, the
// route guard and the nav link.
// Reviewed by: Ryan Ang
// 2026-10-17, Claude Code (Opus 5.5), issue #174: the credit domain's
// Common Pool box is faked (getPoolSummary), so no case reaches a real
// gateway, and the page case expects it beside the Users section.
// Same day: its past-distributions call faked too.
// Author review: Ryan Ang, pending pull request review.
// 2026-10-27, Claude Code (Opus 5.5), issue #242: the Stuck Orders and
// Failed Orders sections against faked credit and order admin calls —
// stuck flagging (finished orders only, DISPUTED never), retry settlement
// and its 409, the threshold presets and truncation notice; the failed
// states and reasons, the closed time and its fallback, and the user,
// reason and date filters together.
// Author review: Ryan Ang, pending pull request review.
// 2026-10-28, Claude Code (Opus 5.5), PR #265 review: held credits for an
// unknown order are named and not counted as in progress; the user lookup
// lists its matches to pick from, with a notice when there are more.
// Author review: Ryan Ang, pending pull request review.
// 2026-10-29, Claude Code (Opus 5.5), issue #241: the Disputes section
// against faked order admin calls — the queue and its count badge, the
// three outcomes and their /resolve bodies, the split adding up to the
// reward, and order-service's 409 for an admin who is a party.
// Author review: Ryan Ang, pending pull request review.

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { creditApi } from '@/features/credit/api'
import { localDayStart } from '@/features/credit/history'
import { orderAdminApi } from '@/features/order/adminApi'
import { orderApi } from '@/features/order/api'
import type { RequestSummary } from '@/features/order/types'
import { adminUserApi } from '@/features/user/adminApi'
import { USERS_PAGE_SIZE } from '@/features/user/components/UsersSection'
import { ApiError } from '@/lib/api/http'
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

function section(name: 'Users') {
  return within(screen.getByRole('region', { name }))
}

// The page appears once the admin route guard's role check resolves, so
// the first query of a test waits for its section.
async function findSection(name: 'Users') {
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
  // the Common Pool box above the Users section (credit domain)
  vi.spyOn(creditApi, 'getPoolSummary').mockResolvedValue({
    poolBalance: 0,
    accounts: 0,
    share: 0,
    remainder: 0,
    lastRun: null,
    nextRunAt: '2026-10-31T16:00:00Z',
  })
  vi.spyOn(creditApi, 'getRedistributions').mockResolvedValue({
    content: [],
    page: 0,
    size: 12,
    totalElements: 0,
    totalPages: 0,
  })
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
  // the order domain's Stuck Orders and Failed Orders sections: nothing
  // held, no failed orders, unless a case says otherwise
  vi.spyOn(creditApi, 'getHeldReservations').mockResolvedValue({
    items: [],
    truncated: false,
  })
  vi.spyOn(orderAdminApi, 'getStatuses').mockResolvedValue({})
  vi.spyOn(orderAdminApi, 'retrySettlement').mockResolvedValue({
    message: 'queued',
  })
  vi.spyOn(orderAdminApi, 'listRequests').mockResolvedValue(pageOf([]))
  // the Disputes section: an empty queue unless a case says otherwise
  vi.spyOn(orderAdminApi, 'listDisputes').mockResolvedValue(pageOf([]))
  vi.spyOn(orderAdminApi, 'resolve').mockResolvedValue({
    message: 'resolved',
  })
  vi.spyOn(orderApi, 'getUsername').mockImplementation(
    async (id) => `user_${id}`,
  )
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

describe('Admin Dashboard page', () => {
  test('shows the Common Pool, order and Users sections', async () => {
    renderAdmin()
    await findSectionTable('Users')

    expect(
      screen
        .getAllByRole('region')
        .map((r) => r.getAttribute('aria-labelledby')),
    ).toEqual([
      'pool-heading',
      'disputes-heading',
      'stuck-orders-heading',
      'failed-orders-heading',
      'users-heading',
    ])
    expect(
      screen.queryByRole('region', { name: 'Suppliers' }),
    ).not.toBeInTheDocument()
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

// --- order domain: Stuck Orders and Failed Orders (issue #242)

function pageOf(content: RequestSummary[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
  }
}

function held(requestId: string, requesterId = '2', amount = 3) {
  return { requestId, requesterId, amount, heldSince: '2026-10-27T01:00:00Z' }
}

function failed(
  id: number,
  change: Partial<RequestSummary> = {},
): RequestSummary {
  return {
    id,
    status: 'CANCELLED',
    closeReason: 'REQUESTER_CANCELLED',
    closedAt: '2026-10-20T04:30:00Z',
    supplierName: 'The Deck',
    dropoffLocation: 'COM3 Level 2',
    item: `Item ${id}`,
    creditAmount: 3,
    deadline: '2026-10-20T06:00:00Z',
    createdAt: '2026-10-20T02:00:00Z',
    autoCompleteAt: null,
    ...change,
  }
}

async function findRegion(name: 'Stuck Orders' | 'Failed Orders') {
  return within(await screen.findByRole('region', { name }))
}

function lastAdminListParams() {
  return vi.mocked(orderAdminApi.listRequests).mock.lastCall?.[0]
}

describe('Stuck Orders section', () => {
  test('flags only held reservations whose order is finished', async () => {
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [
        held('41', '2'),
        held('42'),
        held('43'),
        held('44', '4', 5),
        held('45'),
      ],
      truncated: false,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({
      '41': 'COMPLETED',
      '42': 'DISPUTED', // holds its credits on purpose
      '43': 'ACCEPTED', // still in progress
      '44': 'RESOLVED',
      '45': null, // no such order
    })
    renderAdmin()

    const table = within(
      await (await findRegion('Stuck Orders')).findByRole('table'),
    )
    expect(creditApi.getHeldReservations).toHaveBeenCalledWith('PT1H')
    // one batch call for every held reservation
    expect(orderAdminApi.getStatuses).toHaveBeenCalledTimes(1)
    expect(orderAdminApi.getStatuses).toHaveBeenCalledWith([
      '41',
      '42',
      '43',
      '44',
      '45',
    ])
    expect(table.getByText('#41')).toBeInTheDocument()
    expect(table.getByText('#44')).toBeInTheDocument()
    for (const id of ['#42', '#43', '#45']) {
      expect(table.queryByText(id)).not.toBeInTheDocument()
    }
    expect(await table.findByText('user_2')).toBeInTheDocument()
    expect(table.getByText('5 Credits')).toBeInTheDocument()
    // not stuck in the retry sense, but not hidden either
    expect(
      (await findRegion('Stuck Orders')).getByText(
        '1 held reservation is for an order order-service has no record of: #45.',
      ),
    ).toBeInTheDocument()
  })

  test('an unknown order is not counted as in progress', async () => {
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [held('42'), held('45'), held('46')],
      truncated: false,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({
      '42': 'ACCEPTED',
      '45': null,
    })
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    expect(await region.findByText('No stuck orders.')).toBeInTheDocument()
    expect(
      region.getByText(
        '2 held reservations are for orders order-service has no record of: #45, #46.',
      ),
    ).toBeInTheDocument()
    expect(
      region.getByText(
        '1 of 3 held longer than 1 hour are for orders still in progress.',
      ),
    ).toBeInTheDocument()
  })

  test('says so when nothing is stuck', async () => {
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [held('42')],
      truncated: false,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({ '42': 'DISPUTED' })
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    expect(await region.findByText('No stuck orders.')).toBeInTheDocument()
    expect(
      region.getByText(
        '1 held longer than 1 hour, all for orders still in progress.',
      ),
    ).toBeInTheDocument()
  })

  test('nothing held skips the status call', async () => {
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    expect(
      await region.findByText('No credits have been held longer than 1 hour.'),
    ).toBeInTheDocument()
    expect(orderAdminApi.getStatuses).not.toHaveBeenCalled()
  })

  test('retries a settlement and refreshes on request', async () => {
    const user = userEvent.setup()
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [held('41')],
      truncated: false,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({
      '41': 'COMPLETED',
    })
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    const table = within(await region.findByRole('table'))
    await user.click(
      table.getByRole('button', { name: 'Retry settlement for request #41' }),
    )

    expect(orderAdminApi.retrySettlement).toHaveBeenCalledWith(41)
    expect(await region.findByRole('status')).toHaveTextContent(
      'Settlement for request #41 sent again.',
    )
    expect(table.getByText('Retry sent')).toBeInTheDocument()

    // credit-service has settled it by now
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [],
      truncated: false,
    })
    await user.click(region.getByRole('button', { name: 'Refresh' }))
    expect(await region.findByText('No stuck orders.')).toBeInTheDocument()
    expect(creditApi.getHeldReservations).toHaveBeenCalledTimes(2)
  })

  test("shows order-service's 409 when a retry is refused", async () => {
    const user = userEvent.setup()
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [held('41')],
      truncated: false,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({
      '41': 'CANCELLED',
    })
    vi.mocked(orderAdminApi.retrySettlement).mockRejectedValue(
      new ApiError(409, {
        status: 409,
        detail: 'Request 41 has no settlement event to retry',
      }),
    )
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    await user.click(
      within(await region.findByRole('table')).getByRole('button', {
        name: 'Retry settlement for request #41',
      }),
    )

    expect(await region.findByRole('alert')).toHaveTextContent(
      'Request #41: Request 41 has no settlement event to retry',
    )
    // still offered: nothing was queued
    expect(
      within(region.getByRole('table')).getByRole('button', {
        name: 'Retry settlement for request #41',
      }),
    ).toBeEnabled()
  })

  test('the threshold is chosen from presets and remembered', async () => {
    const user = userEvent.setup()
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    await user.selectOptions(
      region.getByLabelText('Held longer than'),
      '24 hours',
    )

    await waitFor(() =>
      expect(creditApi.getHeldReservations).toHaveBeenLastCalledWith('P1D'),
    )
    expect(localStorage.getItem('foc.admin.stuckThreshold')).toBe('P1D')
  })

  test('a remembered threshold is used on the next visit', async () => {
    localStorage.setItem('foc.admin.stuckThreshold', 'P7D')
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    expect(region.getByLabelText('Held longer than')).toHaveValue('P7D')
    expect(creditApi.getHeldReservations).toHaveBeenCalledWith('P7D')
    expect(creditApi.getHeldReservations).not.toHaveBeenCalledWith('PT1H')
  })

  test('says when the list was cut short', async () => {
    vi.mocked(creditApi.getHeldReservations).mockResolvedValue({
      items: [held('41')],
      truncated: true,
    })
    vi.mocked(orderAdminApi.getStatuses).mockResolvedValue({ '41': 'EXPIRED' })
    renderAdmin()

    const region = await findRegion('Stuck Orders')
    expect(
      await region.findByText('Showing the oldest 100; more are held.'),
    ).toBeInTheDocument()
  })
})

describe('Failed Orders section', () => {
  test('lists failed orders with their reason and when they closed', async () => {
    vi.mocked(orderAdminApi.listRequests).mockResolvedValue(
      pageOf([
        failed(7),
        failed(8, { status: 'EXPIRED', closeReason: 'DEADLINE_PASSED' }),
        // closed before closedAt was recorded
        failed(9, { status: 'REJECTED', closeReason: null, closedAt: null }),
      ]),
    )
    renderAdmin()

    const region = await findRegion('Failed Orders')
    const table = within(await region.findByRole('table'))
    expect(lastAdminListParams()).toEqual({
      page: 0,
      size: 20,
      state: ['REJECTED', 'CANCELLED', 'EXPIRED'],
      closeReason: undefined,
      userId: undefined,
      createdFrom: undefined,
      createdTo: undefined,
      closedFrom: undefined,
      closedTo: undefined,
    })
    const row = (id: string) => {
      const tr = table.getByText(id).closest('tr')
      if (!tr) throw new Error(`No row for ${id}`)
      return within(tr)
    }
    expect(row('#7').getByText('Cancelled by requester')).toBeInTheDocument()
    expect(row('#8').getByText('Deadline passed')).toBeInTheDocument()
    expect(row('#9').getByText('Not recorded')).toBeInTheDocument()
    expect(
      row('#9').getByText('(created; close time unknown)'),
    ).toBeInTheDocument()
    expect(
      row('#7').queryByText('(created; close time unknown)'),
    ).not.toBeInTheDocument()
  })

  test('does not offer resolved disputes as a reason', async () => {
    renderAdmin()

    const region = await findRegion('Failed Orders')
    const reasons = within(region.getByLabelText('Close reason'))
      .getAllByRole('option')
      .map((o) => o.textContent)
    expect(reasons).toEqual([
      'All reasons',
      'Cancelled by requester',
      'Credit reservation timed out',
      'Not enough credits',
      'Invalid reward',
      'Deadline passed',
    ])
    expect(await region.findByText('No failed orders.')).toBeInTheDocument()
  })

  test('user, reason and dates filter together', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const region = await findRegion('Failed Orders')

    await user.selectOptions(
      region.getByLabelText('Close reason'),
      'Credit reservation timed out',
    )
    await user.type(
      region.getByLabelText('User (requester or courier)'),
      'STUDENT_ALEX',
    )
    await user.click(region.getByRole('button', { name: 'Find' }))
    // looked up through user-service, removed accounts included
    expect(adminUserApi.listUsers).toHaveBeenLastCalledWith({
      search: 'STUDENT_ALEX',
      includeDeleted: true,
      page: 0,
      size: 20,
    })
    await user.click(
      await region.findByRole('button', { name: 'Filter by student_alex' }),
    )
    expect(
      await region.findByRole('button', { name: 'Clear user student_alex' }),
    ).toBeInTheDocument()
    await user.type(region.getByLabelText('From'), '2026-10-01')
    await user.type(region.getByLabelText('To'), '2026-10-31')

    await waitFor(() =>
      expect(lastAdminListParams()).toMatchObject({
        closeReason: ['RESERVATION_TIMEOUT'],
        userId: 2,
        closedFrom: localDayStart('2026-10-01'),
        closedTo: localDayStart('2026-10-31', 1),
        createdFrom: undefined,
      }),
    )

    // the same days, by creation instead
    await user.selectOptions(
      region.getByLabelText('Date filter applies to'),
      'When it was created',
    )
    await waitFor(() =>
      expect(lastAdminListParams()).toMatchObject({
        userId: 2,
        createdFrom: localDayStart('2026-10-01'),
        createdTo: localDayStart('2026-10-31', 1),
        closedFrom: undefined,
        closedTo: undefined,
      }),
    )

    await user.click(
      region.getByRole('button', { name: 'Clear user student_alex' }),
    )
    await waitFor(() => expect(lastAdminListParams()?.userId).toBeUndefined())
  })

  test('a broad lookup lists its matches to pick from', async () => {
    const user = userEvent.setup()
    users = [...seedUsers, ...manyUsers(25)]
    renderAdmin()
    const region = await findRegion('Failed Orders')

    await user.type(
      region.getByLabelText('User (requester or courier)'),
      'user_',
    )
    await user.click(region.getByRole('button', { name: 'Find' }))

    const matches = within(
      await region.findByRole('list', { name: 'Users matching "user_"' }),
    )
    // nothing picked on the admin's behalf
    expect(lastAdminListParams()?.userId).toBeUndefined()
    expect(matches.getAllByRole('button')).toHaveLength(20)
    expect(
      region.getByText(
        'Showing 20 of 25 matches; type more to narrow them down.',
      ),
    ).toBeInTheDocument()

    await user.click(
      matches.getByRole('button', { name: `Filter by ${nth(3)}` }),
    )
    await waitFor(() => expect(lastAdminListParams()?.userId).toBe(1002))
    expect(
      region.queryByRole('list', { name: 'Users matching "user_"' }),
    ).not.toBeInTheDocument()
  })

  test('an unknown user is reported and not queried', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const region = await findRegion('Failed Orders')
    await waitFor(() => expect(orderAdminApi.listRequests).toHaveBeenCalled())
    const calls = vi.mocked(orderAdminApi.listRequests).mock.calls.length

    await user.type(
      region.getByLabelText('User (requester or courier)'),
      'nobody_here',
    )
    await user.click(region.getByRole('button', { name: 'Find' }))

    expect(await region.findByRole('alert')).toHaveTextContent(
      'No user matches "nobody_here".',
    )
    expect(orderAdminApi.listRequests).toHaveBeenCalledTimes(calls)
  })

  test('a start date after the end date is refused before querying', async () => {
    const user = userEvent.setup()
    renderAdmin()
    const region = await findRegion('Failed Orders')
    await waitFor(() => expect(orderAdminApi.listRequests).toHaveBeenCalled())

    await user.type(region.getByLabelText('To'), '2026-10-01')
    const calls = vi.mocked(orderAdminApi.listRequests).mock.calls.length
    await user.type(region.getByLabelText('From'), '2026-10-31')

    expect(await region.findByRole('alert')).toHaveTextContent(
      'The start date cannot be after the end date.',
    )
    expect(orderAdminApi.listRequests).toHaveBeenCalledTimes(calls)
  })
})

// --- order domain: Disputes (issue #241)

function disputed(
  id: number,
  change: Partial<NonNullable<RequestSummary['dispute']>> = {},
): RequestSummary {
  return {
    id,
    status: 'DISPUTED',
    closeReason: null,
    closedAt: null,
    supplierName: 'The Deck',
    dropoffLocation: 'COM3 Level 2',
    item: `Item ${id}`,
    creditAmount: 3,
    deadline: '2026-10-29T06:00:00Z',
    createdAt: '2026-10-29T02:00:00Z',
    autoCompleteAt: null,
    dispute: {
      category: 'WRONG_ITEM',
      disputeNote: 'Got a mocha',
      raisedAt: '2026-10-29T05:00:00Z',
      autoEscalated: false,
      resolvedAt: null,
      resolvedBy: null,
      courierAmount: null,
      requesterAmount: null,
      resolutionNote: null,
      ...change,
    },
  }
}

async function findDisputes() {
  return within(await screen.findByRole('region', { name: /^Disputes/ }))
}

async function openResolve(
  user: ReturnType<typeof userEvent.setup>,
  id: number,
) {
  const region = await findDisputes()
  await user.click(
    await region.findByRole('button', { name: `Resolve request #${id}` }),
  )
  return within(screen.getByRole('dialog', { name: `Resolve request #${id}` }))
}

describe('Disputes section', () => {
  beforeEach(() => {
    vi.spyOn(orderApi, 'getRequest').mockImplementation(async (id) => ({
      ...disputed(id),
      requesterId: 2,
      courierId: 4,
      supplierId: 1,
      acceptedAt: null,
      collectedAt: null,
      arrivedAt: null,
    }))
  })

  test('lists the DISPUTED requests with a count badge', async () => {
    vi.mocked(orderAdminApi.listDisputes).mockResolvedValue(
      pageOf([
        disputed(7),
        disputed(8, {
          category: 'NOT_DELIVERED',
          disputeNote: 'auto-escalated: not delivered by deadline',
          autoEscalated: true,
        }),
      ]),
    )
    renderAdmin()

    const region = await findDisputes()
    expect(await region.findByLabelText('2 open')).toHaveTextContent('2')
    expect(orderAdminApi.listDisputes).toHaveBeenCalledWith(0, 20)
    expect(region.getByText('Wrong item')).toBeInTheDocument()
    expect(region.getByText('Got a mocha')).toBeInTheDocument()
    expect(region.getByText('Not delivered')).toBeInTheDocument()
    expect(region.getByText('Auto-escalated')).toBeInTheDocument()
  })

  test('says so when there are none, with no badge', async () => {
    renderAdmin()

    const region = await findDisputes()
    expect(await region.findByText('No open disputes.')).toBeInTheDocument()
    expect(region.queryByLabelText(/open$/)).not.toBeInTheDocument()
  })

  test('pays the courier or refunds the requester in full', async () => {
    const user = userEvent.setup()
    vi.mocked(orderAdminApi.listDisputes).mockResolvedValue(
      pageOf([disputed(7)]),
    )
    renderAdmin()

    let dialog = await openResolve(user, 7)
    // the parties come from the request's detail
    expect(await dialog.findByText('user_2')).toBeInTheDocument()
    expect(dialog.getByText('user_4')).toBeInTheDocument()
    await user.click(dialog.getByLabelText('Pay the courier'))
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))
    expect(orderAdminApi.resolve).toHaveBeenLastCalledWith(7, {
      courierAmount: 3,
      requesterAmount: 0,
    })
    expect(
      await (
        await findDisputes()
      ).findByText(
        'Request #7 resolved: 3 Credits to the courier, 0 Credits back to the requester.',
      ),
    ).toBeInTheDocument()
    // the queue reloads
    expect(orderAdminApi.listDisputes).toHaveBeenCalledTimes(2)

    dialog = await openResolve(user, 7)
    await user.click(dialog.getByLabelText('Refund the requester'))
    await user.type(dialog.getByLabelText(/Note/), 'Item never arrived')
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))
    expect(orderAdminApi.resolve).toHaveBeenLastCalledWith(7, {
      courierAmount: 0,
      requesterAmount: 3,
      note: 'Item never arrived',
    })
  })

  test('a split always adds up to the reward', async () => {
    const user = userEvent.setup()
    vi.mocked(orderAdminApi.listDisputes).mockResolvedValue(
      pageOf([disputed(7)]),
    )
    renderAdmin()

    const dialog = await openResolve(user, 7)
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))
    expect(dialog.getByText('Choose how to resolve it.')).toBeInTheDocument()

    await user.click(dialog.getByLabelText('Split the reward'))
    const share = dialog.getByLabelText('Credits to the courier')
    await user.type(share, '4')
    expect(
      dialog.getByText('Enter a whole number from 0 to 3.'),
    ).toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))
    expect(orderAdminApi.resolve).not.toHaveBeenCalled()

    await user.clear(share)
    await user.type(share, '1')
    expect(
      dialog.getByText('2 Credits back to the requester.'),
    ).toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))
    expect(orderAdminApi.resolve).toHaveBeenCalledWith(7, {
      courierAmount: 1,
      requesterAmount: 2,
    })
  })

  test("shows order-service's 409 when the admin is a party", async () => {
    const user = userEvent.setup()
    vi.mocked(orderAdminApi.listDisputes).mockResolvedValue(
      pageOf([disputed(7)]),
    )
    vi.mocked(orderAdminApi.resolve).mockRejectedValue(
      new ApiError(409, {
        status: 409,
        detail: 'An admin cannot resolve a dispute on their own request',
      }),
    )
    renderAdmin()

    const dialog = await openResolve(user, 7)
    await user.click(dialog.getByLabelText('Refund the requester'))
    await user.click(dialog.getByRole('button', { name: 'Resolve' }))

    expect(await dialog.findByRole('alert')).toHaveTextContent(
      'An admin cannot resolve a dispute on their own request',
    )
    expect(orderAdminApi.listDisputes).toHaveBeenCalledTimes(1)
  })
})
