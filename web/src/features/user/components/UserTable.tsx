// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: admin users list — a table at md+ and stacked cards below,
// per web/docs/wireframes/admin-dashboard.png (Users section).
// Reviewed by: [pending]

import type { AdminUser, UserRole } from '../types'

const roleLabels: Record<UserRole, string> = {
  USER: 'User',
  ADMIN: 'Admin',
  OWNER: 'Owner',
}

interface UserTableProps {
  users: AdminUser[]
  busyUserId: number | null
  onChangeRole: (user: AdminUser, role: UserRole) => void
  onRemove: (user: AdminUser) => void
}

interface UserActionsProps {
  user: AdminUser
  busy: boolean
  onChangeRole: (user: AdminUser, role: UserRole) => void
  onRemove: (user: AdminUser) => void
}

// The owner account has no actions: it is created once via setup-owner
// and is not managed from this screen.
function UserActions({ user, busy, onChangeRole, onRemove }: UserActionsProps) {
  if (user.role === 'OWNER') {
    return <span className="text-sm text-gray-400">—</span>
  }

  const linkClass =
    'text-sm font-medium text-gray-900 hover:underline disabled:cursor-not-allowed disabled:opacity-50'

  return (
    <div className="flex flex-wrap gap-x-3 gap-y-1">
      {user.role === 'USER' ? (
        <button
          type="button"
          disabled={busy}
          onClick={() => onChangeRole(user, 'ADMIN')}
          className={linkClass}
        >
          Promote to Admin
        </button>
      ) : (
        <button
          type="button"
          disabled={busy}
          onClick={() => onChangeRole(user, 'USER')}
          className={linkClass}
        >
          Demote to User
        </button>
      )}
      <button
        type="button"
        disabled={busy}
        onClick={() => onRemove(user)}
        className="text-sm font-medium text-red-600 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
      >
        Remove Account
      </button>
    </div>
  )
}

export function UserTable({
  users,
  busyUserId,
  onChangeRole,
  onRemove,
}: UserTableProps) {
  return (
    <>
      {/* Desktop / tablet */}
      <div className="hidden overflow-hidden rounded-lg border border-gray-200 bg-white md:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
            <tr>
              <th scope="col" className="px-4 py-3">
                Username
              </th>
              <th scope="col" className="px-4 py-3">
                Email
              </th>
              <th scope="col" className="px-4 py-3">
                Role
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {users.map((user) => (
              <tr key={user.id}>
                <td className="px-4 py-3 font-medium text-gray-900">
                  {user.username}
                </td>
                <td className="px-4 py-3 text-gray-600">{user.email}</td>
                <td className="px-4 py-3 text-gray-600">
                  {roleLabels[user.role]}
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end">
                    <UserActions
                      user={user}
                      busy={busyUserId === user.id}
                      onChangeRole={onChangeRole}
                      onRemove={onRemove}
                    />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile */}
      <ul className="space-y-3 md:hidden">
        {users.map((user) => (
          <li
            key={user.id}
            className="rounded-lg border border-gray-200 bg-white p-4"
          >
            <p className="font-medium text-gray-900">{user.username}</p>
            <p className="text-sm text-gray-600">{user.email}</p>
            <p className="text-xs text-gray-400">
              Role: {roleLabels[user.role]}
            </p>
            {/* The table's "—" placeholder reads as noise on a card. */}
            {user.role !== 'OWNER' && (
              <div className="mt-3">
                <UserActions
                  user={user}
                  busy={busyUserId === user.id}
                  onChangeRole={onChangeRole}
                  onRemove={onRemove}
                />
              </div>
            )}
          </li>
        ))}
      </ul>
    </>
  )
}
