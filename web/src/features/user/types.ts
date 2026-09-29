// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (list response and query types for the #96 endpoints).
// Scope: user domain types for the admin user-management screen.
// Mirrors user-service's UserResponse DTO and the Spring PagedModel body
// that GET /users returns (#96) — not a new contract.
// Reviewed by: Ryan Ang

// Role values as stored by user-service (User.role).
export type UserRole = 'USER' | 'ADMIN' | 'OWNER'

export interface AdminUser {
  id: number
  email: string
  username: string
  role: UserRole
  createdAt: string // ISO-8601 instant
}

// GET /users body (Spring Data PagedModel). page.number is 0-based.
export interface AdminUserPage {
  content: AdminUser[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface ListUsersParams {
  search?: string
  role?: UserRole
  page: number // 0-based, as the API expects
  size: number // user-service accepts 20, 50 or 100
}
