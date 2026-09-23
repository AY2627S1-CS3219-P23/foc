// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: in-memory stand-in for the admin user endpoints so the #113
// screen can be built before the backend exists.
//
// TEMPORARY: #96 (admin endpoints) hasn't defined its API yet, so no
// paths are guessed here. Once it lands, replace each function body
// with an apiFetch('user', ...) call (see features/supplier/api.ts for
// the pattern) and delete the mock data — the page only depends on the
// adminUserApi object's signatures.
// Reviewed by: [pending]

import type { AdminUser, UserRole } from './types'

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

let users: AdminUser[] = seedUsers.map((u) => ({ ...u }))

// Small delay so loading/busy states are visible during development.
function delay() {
  return new Promise((resolve) => setTimeout(resolve, 150))
}

function findUser(id: number): AdminUser {
  const user = users.find((u) => u.id === id)
  if (!user) throw new Error('User not found.')
  return user
}

export const adminUserApi = {
  async listUsers(): Promise<AdminUser[]> {
    await delay()
    return users.map((u) => ({ ...u }))
  },

  async changeRole(id: number, role: UserRole): Promise<AdminUser> {
    await delay()
    const user = findUser(id)
    user.role = role
    return { ...user }
  },

  async removeUser(id: number): Promise<void> {
    await delay()
    findUser(id)
    users = users.filter((u) => u.id !== id)
  },
}

// Test helper: restores the seed data between tests.
export function resetMockUsers() {
  users = seedUsers.map((u) => ({ ...u }))
}
