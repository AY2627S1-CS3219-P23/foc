// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (in-memory mock replaced by the #96 endpoints; later that
// day, a dev-only switch to adminApiMock.ts added).
// Scope: admin user endpoints of user-service — GET /users (search, role
// filter, paging), PATCH /users/{id} (role change), DELETE /users/{id}
// (soft delete) — through the shared apiFetch wrapper.
// Reviewed by: [pending] (reviewed by Ryan Ang before the mock switch)

import { apiFetch } from '@/lib/api/http'
import { mockAdminUserApi } from './adminApiMock'
import type {
  AdminUser,
  AdminUserPage,
  ListUsersParams,
  UserRole,
} from './types'

function toQuery(params: ListUsersParams): string {
  const query = new URLSearchParams()
  const search = params.search?.trim()
  if (search) query.set('search', search)
  if (params.role) query.set('role', params.role)
  query.set('page', String(params.page))
  query.set('size', String(params.size))
  return `?${query}`
}

const httpAdminUserApi = {
  listUsers(params: ListUsersParams): Promise<AdminUserPage> {
    return apiFetch<AdminUserPage>('user', `/users${toQuery(params)}`)
  },

  changeRole(id: number, role: UserRole): Promise<AdminUser> {
    return apiFetch<AdminUser>('user', `/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    })
  },

  removeUser(id: number): Promise<void> {
    return apiFetch<void>('user', `/users/${id}`, { method: 'DELETE' })
  },
}

// TEMPORARY: VITE_MOCK_ADMIN_API=true (in web/.env.local) swaps in the
// in-memory mock so the page can be viewed before #91 and CORS land.
export const adminUserApi: typeof httpAdminUserApi =
  import.meta.env.VITE_MOCK_ADMIN_API === 'true'
    ? mockAdminUserApi
    : httpAdminUserApi
