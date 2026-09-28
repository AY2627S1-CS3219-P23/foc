// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (Suppliers section reduced to a placeholder).
// Scope: Admin Dashboard page, per web/docs/wireframes/admin-dashboard.png:
// a Suppliers section and a Users section.
//
// TEMPORARY: the Suppliers section is a placeholder. Supplier management
// belongs to the supplier domain (web/AGENTS.md) and waits on the
// supplier-service list endpoint (PR #134) and its admin CRUD endpoints.
// TEMPORARY: there is no admin role gate because src/shared/auth/
// doesn't exist yet (see its README).
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

      <section className="space-y-4" aria-labelledby="suppliers-heading">
        <h2
          id="suppliers-heading"
          className="text-lg font-semibold text-gray-900"
        >
          Suppliers
        </h2>
        <div className="rounded-lg border border-dashed border-gray-300 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            Supplier management is coming soon.
          </p>
        </div>
      </section>

      <UsersSection />
    </div>
  )
}
