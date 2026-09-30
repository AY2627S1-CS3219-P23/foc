// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (Suppliers section reduced to a placeholder).
// Scope: Admin Dashboard page, per web/docs/wireframes/admin-dashboard.png:
// a Suppliers section and a Users section.
//
// 2026-09-29 (issue #147): the Suppliers section lists suppliers
// (read-only, team decision); its Add/Edit/Delete wait on
// supplier-service's CRUD endpoints (#104). The page is ADMIN/OWNER-only
// through AdminRoute (routes/index.tsx).
// 2026-09-30 (Claude Code, Opus 5.5): the Suppliers section removed, so
// the dashboard shows only the Users section for now (Ryan Ang's
// decision); admins manage suppliers on the Suppliers page.
// Reviewed by: Ryan Ang.

import { UsersSection } from '@/features/user/components/UsersSection'

export function Admin() {
  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">
          Admin Dashboard
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Manage university platform content, system configurations, and campus
          users.
        </p>
      </div>

      <UsersSection />
    </div>
  )
}
