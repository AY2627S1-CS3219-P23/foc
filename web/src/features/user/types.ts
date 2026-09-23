// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: user domain types for the admin user-management screen.
// Mirrors the backend's existing UserResponse DTO (PR #126) — not a new
// contract; revisit once #96 defines the admin endpoints.
// Reviewed by: [pending]

// Role values as stored by user-service (User.role).
export type UserRole = 'USER' | 'ADMIN' | 'OWNER'

export interface AdminUser {
  id: number
  email: string
  username: string
  role: UserRole
  createdAt: string // ISO-8601 instant
}
