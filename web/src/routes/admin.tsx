// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: Admin Dashboard page, per web/docs/wireframes/admin-dashboard.png:
// a Suppliers section and a Users section, each owned by its feature
// folder.
//
// TEMPORARY: there is no admin role gate because src/shared/auth/
// doesn't exist yet (see its README).
// Reviewed by: [pending]

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
