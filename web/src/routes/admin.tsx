// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (Suppliers section reduced to a placeholder).
// Scope: Admin Dashboard page, per web/docs/wireframes/admin-dashboard.png:
// a Suppliers section and a Users section.
//
// 2026-09-29 (issue #147): the Suppliers section lists suppliers
// (read-only first, team decision); 2026-09-30: its Add/Edit/Delete added
// now that supplier-service's CRUD endpoints (#104) have merged. The page
// is ADMIN/OWNER-only through AdminRoute (routes/index.tsx).
// Reviewed by: Ryan Ang.

import { SuppliersAdminSection } from '@/features/supplier/components/SuppliersAdminSection'
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

      <SuppliersAdminSection />

      <UsersSection />
    </div>
  )
}
