// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, issue #112.
// Scope: Profile Details card of the profile page, per
// web/docs/wireframes/profile.png — username, NUS email, role, credit
// status, Edit Profile button.
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): an email
// change parked by #92's PATCH (PR #157) shows under the address with a
// way back into its code dialog — the dialog can be dismissed without
// discarding the change, so there has to be a way to reopen it
// (register.tsx's "Enter your code" link, same reasoning).
// Author review: Leong Wei Zhi (via PR #149).

import type { AdminUser } from '../types'

// Every non-staff account can both request and run errands.
function roleLabel(role: AdminUser['role']) {
  if (role === 'ADMIN') return 'Admin'
  if (role === 'OWNER') return 'Owner'
  return 'Requester / Courier'
}

interface ProfileDetailsCardProps {
  user: AdminUser
  editing: boolean
  onEdit: () => void
  // the address waiting on its own code, if a change is parked
  pendingEmail?: string
  onEnterCode?: () => void
}

export function ProfileDetailsCard({
  user,
  editing,
  onEdit,
  pendingEmail,
  onEnterCode,
}: ProfileDetailsCardProps) {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">Profile Details</h2>
      <dl className="mt-4 space-y-4">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
            Username
          </dt>
          <dd className="mt-0.5 text-sm text-gray-900">{user.username}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
            NUS Email Address
          </dt>
          <dd className="mt-0.5 text-sm text-gray-900">{user.email}</dd>
          {pendingEmail && (
            <dd className="mt-1 text-xs text-gray-500">
              Waiting on a code sent to {pendingEmail}.{' '}
              <button
                type="button"
                onClick={onEnterCode}
                className="cursor-pointer font-medium text-gray-900 underline"
              >
                Enter your code
              </button>
            </dd>
          )}
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
            User Role
          </dt>
          <dd className="mt-0.5 text-sm text-gray-900">
            {roleLabel(user.role)}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
            Credit Status
          </dt>
          {/* Placeholder until credit-service has an API — same status as
              the navbar's CreditsBadge. */}
          <dd className="mt-0.5 text-sm text-gray-900">
            Available: — | Reserved: —
          </dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={onEdit}
        disabled={editing}
        className="mt-5 rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
      >
        Edit Profile
      </button>
    </section>
  )
}
