// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-30, PR #156 re-review.
// Scope: tests for the Suppliers page's failure paths as an admin — a
// failed save shown inside the form (which stays open over the page),
// and a failed delete closing the confirm and naming the supplier —
// against a faked supplier API.
// Author review: Ryan to review via the PR.

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'

import {
  createSupplier,
  deleteSupplier,
  listCategories,
  listSuppliers,
} from '@/features/supplier/api'
import type { PagedResponse, Supplier } from '@/features/supplier/types'
import { adminUserApi } from '@/features/user/adminApi'
import { routes } from '../routes'

vi.mock('@/features/supplier/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/supplier/api')>()),
  listSuppliers: vi.fn(),
  listCategories: vi.fn(),
  createSupplier: vi.fn(),
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

function supplierPage(content: Supplier[]): PagedResponse<Supplier> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
  }
}

// a session whose token carries the ADMIN role (the page reads it from
// the token; the signature is never checked client-side)
function signInAsAdmin() {
  const payload = btoa(JSON.stringify({ sub: '3', role: 'ADMIN' }))
  localStorage.setItem(
    'user',
    JSON.stringify({ accessToken: `header.${payload}.signature` }),
  )
}

function renderSuppliers() {
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/suppliers'] })}
    />,
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.mocked(listSuppliers)
    .mockReset()
    .mockResolvedValue(supplierPage(seedSuppliers))
  vi.mocked(listCategories).mockReset().mockResolvedValue([])
  vi.mocked(createSupplier).mockReset()
  vi.mocked(deleteSupplier).mockReset()
  vi.spyOn(adminUserApi, 'getCurrentUser').mockResolvedValue({
    id: 3,
    email: 'e0999999@u.nus.edu',
    username: 'nus_courier_99',
    role: 'ADMIN',
    createdAt: '2026-09-12T11:40:00Z',
  })
  signInAsAdmin()
})

afterEach(() => {
  localStorage.clear()
})

test('a failed save shows its reason inside the form, which stays open', async () => {
  vi.mocked(createSupplier).mockRejectedValue(
    new Error('Supplier name already exists'),
  )
  const user = userEvent.setup()
  renderSuppliers()
  await screen.findByText('CoffeeBean@Com3')

  await user.click(screen.getByRole('button', { name: '+ Add Supplier' }))
  const form = within(screen.getByRole('dialog', { name: 'Add Supplier' }))
  await user.type(form.getByLabelText(/Supplier Name/), 'CoffeeBean@Com3')
  await user.type(form.getByLabelText(/Location/), 'COM3-01-01')
  await user.type(form.getByLabelText(/Latitude/), '1.29')
  await user.type(form.getByLabelText(/Longitude/), '103.77')
  await user.click(form.getByRole('button', { name: 'Save Supplier' }))

  expect(await form.findByRole('alert')).toHaveTextContent(
    'Supplier name already exists',
  )
  expect(form.getByLabelText(/Supplier Name/)).toHaveValue('CoffeeBean@Com3')
  // only there, not also in the page's banner behind the form
  expect(screen.getAllByRole('alert')).toHaveLength(1)

  // reopening starts without the old error
  await user.click(form.getByRole('button', { name: 'Cancel' }))
  await user.click(screen.getByRole('button', { name: '+ Add Supplier' }))
  expect(
    within(screen.getByRole('dialog', { name: 'Add Supplier' })).queryByRole(
      'alert',
    ),
  ).not.toBeInTheDocument()
})

test('a failed delete closes the confirm and names the supplier', async () => {
  vi.mocked(deleteSupplier).mockRejectedValue(new TypeError('Failed to fetch'))
  const user = userEvent.setup()
  renderSuppliers()

  await user.click(
    await screen.findByRole('button', { name: /Fine Foods UTown/ }),
  )
  // the detail panel renders inline (mobile) and as a side column
  // (desktop); jsdom applies no media queries, so both are present
  await user.click(screen.getAllByRole('button', { name: 'Delete' })[0])
  await user.click(
    within(screen.getByRole('dialog', { name: 'Delete Supplier' })).getByRole(
      'button',
      { name: 'Delete' },
    ),
  )

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Could not delete supplier "Fine Foods UTown".',
  )
  // the confirm closes, or it would cover the banner
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(deleteSupplier).toHaveBeenCalledWith('s2')
})
