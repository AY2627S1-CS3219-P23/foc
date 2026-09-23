// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113.
// Scope: Admin Dashboard "Users" section — list, search by username,
// promote/demote, add credits, remove — per
// web/docs/wireframes/admin-dashboard.png.
//
// TEMPORARY: users and balances come from the in-memory adminUserApi /
// adminCreditApi mocks until #96 and credit-service define their APIs.
// Reviewed by: [pending]

import { useEffect, useMemo, useState } from 'react'

import { adminCreditApi } from '@/features/credit/adminCreditApi'
import { AddCreditsModal } from '@/features/credit/components/AddCreditsModal'
import type { CreditBalance } from '@/features/credit/types'
import { ApiError } from '@/lib/api/http'
import { adminUserApi } from '../adminApi'
import type { AdminUser, UserRole } from '../types'
import { RemoveUserModal } from './RemoveUserModal'
import { UserTable } from './UserTable'

function errorMessage(err: unknown, fallback: string) {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error && err.message) return err.message
  return fallback
}

export function UsersSection() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [balances, setBalances] = useState<Map<number, CreditBalance> | null>(
    null,
  )
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [pendingRemove, setPendingRemove] = useState<AdminUser | null>(null)
  const [creditTarget, setCreditTarget] = useState<AdminUser | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      // Balances come from a different service: if only they fail, still
      // show the users, with "—" in the Credits column.
      const [userResult, balanceResult] = await Promise.allSettled([
        adminUserApi.listUsers(),
        adminCreditApi.listBalances(),
      ])
      if (cancelled) return

      if (userResult.status === 'fulfilled') {
        setUsers(userResult.value)
      } else {
        setError(
          errorMessage(userResult.reason, 'Could not load users. Try again.'),
        )
      }
      if (balanceResult.status === 'fulfilled') {
        setBalances(new Map(balanceResult.value.map((b) => [b.userId, b])))
      } else if (userResult.status === 'fulfilled') {
        setError(
          errorMessage(balanceResult.reason, 'Could not load credit balances.'),
        )
      }
      setLoading(false)
    }

    load()

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

  async function handleAddCredits(amount: number) {
    if (!creditTarget) return
    const target = creditTarget
    setBusyUserId(target.id)
    setError(null)
    try {
      const updated = await adminCreditApi.addCredits(target.id, amount)
      setBalances((prev) => new Map(prev ?? []).set(updated.userId, updated))
    } catch (err) {
      setError(
        errorMessage(err, `Could not add credits to ${target.username}.`),
      )
    } finally {
      setCreditTarget(null)
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
    } catch (err) {
      setError(errorMessage(err, `Could not remove ${target.username}.`))
    } finally {
      setPendingRemove(null)
      setBusyUserId(null)
    }
  }

  return (
    <section className="space-y-4" aria-labelledby="users-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 id="users-heading" className="text-lg font-semibold text-gray-900">
          Users
        </h2>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by username..."
          aria-label="Search by username"
          className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none sm:w-64"
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
          balances={balances}
          busyUserId={busyUserId}
          onChangeRole={handleChangeRole}
          onAddCredits={setCreditTarget}
          onRemove={setPendingRemove}
        />
      )}

      {pendingRemove && (
        <RemoveUserModal
          user={pendingRemove}
          onCancel={() => setPendingRemove(null)}
          onConfirm={handleRemove}
          removing={busyUserId === pendingRemove.id}
        />
      )}

      {creditTarget && (
        <AddCreditsModal
          username={creditTarget.username}
          onCancel={() => setCreditTarget(null)}
          onConfirm={handleAddCredits}
          saving={busyUserId === creditTarget.id}
        />
      )}
    </section>
  )
}
