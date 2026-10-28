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
// 2026-10-17, Claude Code (Opus 5.5), issue #174: the credit domain's
// Common Pool box above the Users section (team decision). Same day,
// author's request: the subtitle under the heading removed.
// Author review: Ryan Ang, pending pull request review.
// 2026-10-27, Claude Code (Opus 5.5), issue #242: the order domain's
// Stuck Orders and Failed Orders sections, between the pool and the users.
// Author review: Ryan Ang, pending pull request review.

import { CommonPoolCard } from '@/features/credit/components/CommonPoolCard'
import { FailedOrdersSection } from '@/features/order/components/FailedOrdersSection'
import { StuckOrdersSection } from '@/features/order/components/StuckOrdersSection'
import { UsersSection } from '@/features/user/components/UsersSection'

export function Admin() {
  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">
          Admin Dashboard
        </h1>
      </div>

      <CommonPoolCard />

      <StuckOrdersSection />

      <FailedOrdersSection />

      <UsersSection />
    </div>
  )
}
