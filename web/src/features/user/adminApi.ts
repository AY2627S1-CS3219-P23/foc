// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (in-memory mock replaced by the #96 endpoints; later that
// day, a dev-only switch to adminApiMock.ts added; then GET /users/me
// added so the page knows which row is the signed-in admin's own; PR #140
// review: the mock switch only works in dev builds, and the mock is
// loaded lazily so it isn't bundled into production).
// Scope: admin user endpoints of user-service — GET /users (search, role
// filter, paging), PATCH /users/{id} (role change), DELETE /users/{id}
// (soft delete), GET /users/me (own profile) — through the shared
// apiFetch wrapper.
// Reviewed by: Ryan Ang

import { apiFetch } from '@/lib/api/http'
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

  getCurrentUser(): Promise<AdminUser> {
    return apiFetch<AdminUser>('user', '/users/me')
  },
}

// TEMPORARY: VITE_MOCK_ADMIN_API=true (in web/.env.local) swaps in the
// in-memory mock so the page can be viewed before #91 and CORS land.
// Dev server only: production builds always use the real calls. The mock
// is imported on first use, so production bundles leave it out entirely.
function loadMock() {
  return import('./adminApiMock').then((m) => m.mockAdminUserApi)
}

const devMockAdminUserApi: typeof httpAdminUserApi = {
  listUsers: async (params) => (await loadMock()).listUsers(params),
  changeRole: async (id, role) => (await loadMock()).changeRole(id, role),
  removeUser: async (id) => (await loadMock()).removeUser(id),
  getCurrentUser: async () => (await loadMock()).getCurrentUser(),
}

export const adminUserApi: typeof httpAdminUserApi =
  import.meta.env.DEV && import.meta.env.VITE_MOCK_ADMIN_API === 'true'
    ? devMockAdminUserApi
    : httpAdminUserApi
