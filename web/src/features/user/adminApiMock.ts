// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-28, issue #113.
// Scope: in-memory stand-in for the admin user endpoints, so the Users
// section can be viewed in the browser before #91 (login) and CORS on
// user-service exist. Same signatures as the real adminUserApi; used only
// when VITE_MOCK_ADMIN_API=true (see adminApi.ts). Filters and pages like
// GET /users: search matches id/username/email, page is 0-based.
//
// TEMPORARY: delete this file (and the switch in adminApi.ts) once the
// real endpoints are reachable from the browser.
// Reviewed by: Ryan Ang

import type {
  AdminUser,
  AdminUserPage,
  ListUsersParams,
  UserRole,
} from './types'

const names = [
  'student_alex',
  'nus_courier_99',
  'utown_runner',
  'pgp_night_owl',
  'kent_ridge_kai',
  'science_sam',
  'biz_bella',
  'engin_ethan',
  'fass_fiona',
  'soc_sean',
  'rvrc_rachel',
  'tembusu_tom',
  'cinnamon_chloe',
  'eusoff_eric',
  'raffles_rina',
  'kr_hall_ken',
  'sheares_shan',
  'temasek_tim',
  'lh_lina',
  'yih_yusuf',
  'central_lib_cara',
  'deck_diner_dan',
  'frontier_faye',
  'techno_edge_ted',
  'com3_coder',
  'medicine_mei',
  'law_link_liam',
  'music_mira',
  'dentistry_dev',
]

const seedUsers: readonly AdminUser[] = [
  {
    id: 1,
    email: 'e0000001@u.nus.edu',
    username: 'foc_owner',
    role: 'OWNER',
    createdAt: '2026-09-01T09:00:00Z',
  },
  ...names.map(
    (username, i): AdminUser => ({
      id: i + 2,
      email: `e${1000100 + i}@u.nus.edu`,
      username,
      role: i % 7 === 1 ? 'ADMIN' : 'USER',
      createdAt: new Date(Date.UTC(2026, 8, 2 + (i % 25), 8)).toISOString(),
    }),
  ),
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

export const mockAdminUserApi = {
  async listUsers(params: ListUsersParams): Promise<AdminUserPage> {
    await delay()
    const search = params.search?.trim().toLowerCase()
    const matches = users.filter(
      (u) =>
        (!params.role || u.role === params.role) &&
        (!search ||
          String(u.id).includes(search) ||
          u.username.toLowerCase().includes(search) ||
          u.email.toLowerCase().includes(search)),
    )
    const start = params.page * params.size
    return {
      content: matches.slice(start, start + params.size).map((u) => ({ ...u })),
      page: {
        size: params.size,
        number: params.page,
        totalElements: matches.length,
        totalPages: Math.ceil(matches.length / params.size),
      },
    }
  },

  async changeRole(id: number, role: UserRole): Promise<AdminUser> {
    await delay()
    const user = findUser(id)
    if (user.role === 'OWNER') throw new Error('The owner cannot be changed.')
    user.role = role
    return { ...user }
  },

  async removeUser(id: number): Promise<void> {
    await delay()
    findUser(id)
    users = users.filter((u) => u.id !== id)
  },
}
