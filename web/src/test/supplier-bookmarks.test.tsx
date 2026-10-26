// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-26, issues #225/#227 (N8).
// Scope: tests for the Suppliers tab's bookmark flag (toggle, reload to
// page 1 in the default view, in-place flip when filtered, the 5-bookmark
// cap on the client and the server's refusal) and its Create Request
// button, plus the create-request form's ?supplierId= prefill. The
// supplier API module is mocked wholesale; the harness follows
// credits.test.tsx (memory router over the real route table, a stored
// session to pass ProtectedRoute).
// Author review: [pending]

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import { creditApi } from '@/features/credit/api'
import { orderApi } from '@/features/order/api'
import * as supplierApi from '@/features/supplier/api'
import type { Supplier } from '@/features/supplier/types'
import { adminUserApi } from '@/features/user/adminApi'
import { ApiError } from '@/lib/api/http'
import { routes } from '../routes'

vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: '',
    supplier: '',
    order: '',
    credit: '',
    notification: '',
  },
}))

vi.mock('@/features/supplier/api', () => ({
  listSuppliers: vi.fn(),
  listCategories: vi.fn(),
  listBookmarks: vi.fn(),
  addBookmark: vi.fn(),
  removeBookmark: vi.fn(),
  createSupplier: vi.fn(),
  updateSupplier: vi.fn(),
  deleteSupplier: vi.fn(),
  getSupplier: vi.fn(),
}))

function supplier(id: number, name: string, bookmarked = false): Supplier {
  return {
    id: String(id),
    name,
    location: 'Utown',
    latitude: 1.3,
    longitude: 103.7,
    categories: ['Food'],
    openingTime: '09:00',
    closingTime: '18:00',
    description: '',
    bookmarked,
  }
}

function pageOf(content: Supplier[], page = 0) {
  return {
    content,
    page,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
  }
}

function renderAt(path: string) {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: [path] })}
    />,
  )
}

function mockUser(role: 'USER' | 'ADMIN') {
  vi.spyOn(adminUserApi, 'getCurrentUser').mockResolvedValue({
    id: 3,
    email: 'e1000101@u.nus.edu',
    username: 'nus_courier_99',
    role,
    createdAt: '2026-09-03T08:00:00Z',
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.setItem('user', JSON.stringify({ accessToken: 'test-token' }))
  mockUser('USER')
  vi.spyOn(creditApi, 'getBalance').mockResolvedValue({
    available: 42,
    reserved: 8,
    total: 50,
  })
  vi.spyOn(creditApi, 'getExpiries').mockResolvedValue({ items: [] })
  vi.mocked(supplierApi.listCategories).mockResolvedValue(['Food'])
  vi.mocked(supplierApi.listBookmarks).mockResolvedValue({
    count: 1,
    limit: 5,
    bookmarks: [],
  })
  vi.mocked(supplierApi.addBookmark).mockResolvedValue(undefined)
  vi.mocked(supplierApi.removeBookmark).mockResolvedValue(undefined)
  vi.mocked(supplierApi.listSuppliers).mockResolvedValue(
    pageOf([supplier(1, 'A Hot Hideout', true), supplier(2, 'Cool Spot')]),
  )
})

afterEach(() => {
  localStorage.clear()
})

describe('bookmark flag', () => {
  it('shows a flag on every card, pressed only for bookmarked ones', async () => {
    renderAt('/suppliers')
    expect(
      await screen.findByRole('button', {
        name: 'Remove bookmark: A Hot Hideout',
      }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(
      screen.getByRole('button', { name: 'Bookmark: Cool Spot' }),
    ).toHaveAttribute('aria-pressed', 'false')
  })

  it('bookmarking reloads the list from page 1 and does not open the card', async () => {
    const user = userEvent.setup()
    renderAt('/suppliers')
    await user.click(
      await screen.findByRole('button', { name: 'Bookmark: Cool Spot' }),
    )

    expect(supplierApi.addBookmark).toHaveBeenCalledWith('2')
    await waitFor(() =>
      expect(supplierApi.listSuppliers).toHaveBeenCalledTimes(2),
    )
    expect(vi.mocked(supplierApi.listSuppliers).mock.calls[1][0]).toMatchObject(
      { page: 0 },
    )
    expect(screen.queryByText('Active supplier detail')).not.toBeInTheDocument()
  })

  it('un-bookmarking reloads the same page', async () => {
    const user = userEvent.setup()
    renderAt('/suppliers')
    await user.click(
      await screen.findByRole('button', {
        name: 'Remove bookmark: A Hot Hideout',
      }),
    )

    expect(supplierApi.removeBookmark).toHaveBeenCalledWith('1')
    await waitFor(() =>
      expect(supplierApi.listSuppliers).toHaveBeenCalledTimes(2),
    )
  })

  it('flips the flag in place, without reloading, when a search is active', async () => {
    const user = userEvent.setup()
    renderAt('/suppliers')
    await screen.findByText('Cool Spot')
    await user.type(screen.getByPlaceholderText('Search suppliers...'), 'cool')
    await waitFor(() =>
      expect(supplierApi.listSuppliers).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'cool' }),
      ),
    )
    const calls = vi.mocked(supplierApi.listSuppliers).mock.calls.length

    await user.click(
      await screen.findByRole('button', { name: 'Bookmark: Cool Spot' }),
    )

    expect(
      await screen.findByRole('button', { name: 'Remove bookmark: Cool Spot' }),
    ).toBeInTheDocument()
    expect(supplierApi.listSuppliers).toHaveBeenCalledTimes(calls)
  })

  it('blocks a sixth bookmark on the client with the limit message', async () => {
    vi.mocked(supplierApi.listBookmarks).mockResolvedValue({
      count: 5,
      limit: 5,
      bookmarks: [],
    })
    const user = userEvent.setup()
    renderAt('/suppliers')
    await waitFor(() => expect(supplierApi.listBookmarks).toHaveBeenCalled())
    await user.click(
      await screen.findByRole('button', { name: 'Bookmark: Cool Spot' }),
    )

    expect(supplierApi.addBookmark).not.toHaveBeenCalled()
    expect(await screen.findByText(/limit of 5 bookmarks/)).toBeInTheDocument()
  })

  it("shows the server's refusal, e.g. bookmarking too fast", async () => {
    vi.mocked(supplierApi.addBookmark).mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        detail: 'You are bookmarking too fast, please try again.',
      }),
    )
    const user = userEvent.setup()
    renderAt('/suppliers')
    await user.click(
      await screen.findByRole('button', { name: 'Bookmark: Cool Spot' }),
    )

    expect(
      await screen.findByText(
        'You are bookmarking too fast, please try again.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Bookmark: Cool Spot' }),
    ).toHaveAttribute('aria-pressed', 'false')
  })
})

describe('Create Request button', () => {
  it('links to the form with the active supplier', async () => {
    const user = userEvent.setup()
    renderAt('/suppliers')
    await user.click(await screen.findByText('Cool Spot'))

    // the panel renders twice (inline below lg, sidebar at lg); CSS hides one
    const links = await screen.findAllByRole('link', { name: 'Create Request' })
    for (const link of links) {
      expect(link).toHaveAttribute('href', '/my-requests/new?supplierId=2')
    }
  })

  it('is shown to admins too, above Edit and Delete', async () => {
    mockUser('ADMIN')
    // the page reads the role from the session token's claims
    localStorage.setItem(
      'user',
      JSON.stringify({ accessToken: `h.${btoa('{"role":"ADMIN"}')}.s` }),
    )
    const user = userEvent.setup()
    renderAt('/suppliers')
    await user.click(await screen.findByText('Cool Spot'))

    const [create] = await screen.findAllByRole('link', {
      name: 'Create Request',
    })
    const [edit] = screen.getAllByRole('button', { name: 'Edit' })
    expect(
      create.compareDocumentPosition(edit) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })
})

describe('create-request prefill', () => {
  beforeEach(() => {
    vi.spyOn(orderApi, 'listSupplierOptions').mockResolvedValue([
      { id: '1', name: 'A Hot Hideout', location: 'PGP' },
      { id: '2', name: 'Cool Spot', location: 'Utown' },
    ])
  })

  it('preselects the supplier named by ?supplierId=', async () => {
    renderAt('/my-requests/new?supplierId=2')
    const select = await screen.findByLabelText('Pickup supplier')
    await waitFor(() => expect(select).toHaveValue('2'))
  })

  it('ignores an id that is not in the supplier list', async () => {
    renderAt('/my-requests/new?supplierId=999')
    const select = await screen.findByLabelText('Pickup supplier')
    await screen.findByRole('option', { name: /Cool Spot/ })
    expect(select).toHaveValue('')
  })
})
