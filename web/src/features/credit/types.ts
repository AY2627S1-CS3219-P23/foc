// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: credit domain types for the admin dashboard's Credits column
// (web/docs/wireframes/admin-dashboard.png: "Credits (av/res)").
// Placeholder until credit-service defines its API.
// Reviewed by: [pending]

export interface CreditBalance {
  userId: number
  available: number
  reserved: number
}
