// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: Admin Dashboard page — user management (list, search by
// username, promote/demote, remove), per
// web/docs/wireframes/admin-dashboard.png (Users section only; the
// Suppliers section and Add Credits belong to other owners).
//
// TEMPORARY: data comes from the in-memory adminUserApi mock until #96
// defines the admin endpoints, and there is no admin role gate because
// src/shared/auth/ doesn't exist yet (see its README).
// Reviewed by: [pending]

import { useEffect, useMemo, useState } from 'react'

import { adminUserApi } from '@/features/user/adminApi'
import { RemoveUserModal } from '@/features/user/components/RemoveUserModal'
import { UserTable } from '@/features/user/components/UserTable'
import type { AdminUser, UserRole } from '@/features/user/types'
import { ApiError } from '@/lib/api/http'

function errorMessage(err: unknown, fallback: string) {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error && err.message) return err.message
  return fallback
}

export function Admin() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [pendingRemove, setPendingRemove] = useState<AdminUser | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadUsers() {
      try {
        const result = await adminUserApi.listUsers()
        if (!cancelled) setUsers(result)
      } catch (err) {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load users. Try again.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadUsers()

    return () => {
      cancelled = true
    }
  }, [])

  const visibleUsers = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q ? users.filter((u) => u.username.toLowerCase().includes(q)) : users
  }, [users, query])

  async function handleChangeRole(user: AdminUser, role: UserRole) {
    setBusyUserId(user.id)
    setError(null)
    try {
      const updated = await adminUserApi.changeRole(user.id, role)
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)))
    } catch (err) {
      setError(errorMessage(err, `Could not change ${user.username}'s role.`))
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleRemove() {
    if (!pendingRemove) return
    const target = pendingRemove
    setBusyUserId(target.id)
    setError(null)
    try {
      await adminUserApi.removeUser(target.id)
      setUsers((prev) => prev.filter((u) => u.id !== target.id))
      setPendingRemove(null)
    } catch (err) {
      setError(errorMessage(err, `Could not remove ${target.username}.`))
      setPendingRemove(null)
    } finally {
      setBusyUserId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">
          Admin Dashboard
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Manage campus users and their roles.
        </p>
      </div>

      <section className="space-y-4" aria-labelledby="users-heading">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h2
            id="users-heading"
            className="text-lg font-semibold text-gray-900"
          >
            Users
          </h2>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by username..."
            aria-label="Search by username"
            className="w-full rounded-md border border-gray-200 px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none sm:w-64"
          />
        </div>

        {error && (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {error}
          </p>
        )}

        {loading ? (
          <p className="text-sm text-gray-500">Loading users...</p>
        ) : visibleUsers.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
            <p className="font-medium text-gray-900">
              {query ? 'No users match your search.' : 'No users yet.'}
            </p>
          </div>
        ) : (
          <UserTable
            users={visibleUsers}
            busyUserId={busyUserId}
            onChangeRole={handleChangeRole}
            onRemove={setPendingRemove}
          />
        )}
      </section>

      {pendingRemove && (
        <RemoveUserModal
          user={pendingRemove}
          onCancel={() => setPendingRemove(null)}
          onConfirm={handleRemove}
          removing={busyUserId === pendingRemove.id}
        />
      )}
    </div>
  )
}
