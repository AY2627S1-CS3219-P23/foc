// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (credit balances and Add Credits removed; wired to the #96
// endpoints with server-side search, role filter and paging; PR #140
// review: reload after a role change, step back a page when its last
// row is removed, and GET /users/me so the own row has no Remove;
// promote/demote now asks for confirmation first).
// Scope: Admin Dashboard "Users" section — list, search, role filter,
// paging, promote/demote, remove — per
// web/docs/wireframes/admin-dashboard.png.
// Reviewed by: Ryan Ang

import { useEffect, useState } from 'react'

import { ApiError } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'
import { adminUserApi } from '../adminApi'
import type { AdminUser, AdminUserPage, UserRole } from '../types'
import { ChangeRoleModal } from './ChangeRoleModal'
import { RemoveUserModal } from './RemoveUserModal'
import { UserTable } from './UserTable'

// Wait this long after the last keystroke before searching.
const SEARCH_DEBOUNCE_MS = 300
// user-service accepts 20, 50 or 100.
export const USERS_PAGE_SIZE = 100

const roleOptions: { value: UserRole | ''; label: string }[] = [
  { value: '', label: 'All roles' },
  { value: 'USER', label: 'User' },
  { value: 'ADMIN', label: 'Admin' },
  { value: 'OWNER', label: 'Owner' },
]

function errorMessage(err: unknown, fallback: string) {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error && err.message) return err.message
  return fallback
}

// The result of one list request, tagged with the query it answered so a
// newer query shows as loading until its own result arrives.
interface LoadResult {
  key: string
  data: AdminUserPage | null
  error: string | null
}

export function UsersSection() {
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [page, setPage] = useState(1) // 1-based; the API is 0-based
  // Bumped to reload the current query after a change on the server.
  const [reloadCount, setReloadCount] = useState(0)
  const [result, setResult] = useState<LoadResult | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [pendingRemove, setPendingRemove] = useState<AdminUser | null>(null)
  const [pendingRoleChange, setPendingRoleChange] = useState<{
    user: AdminUser
    role: UserRole
  } | null>(null)
  const [currentUserId, setCurrentUserId] = useState<number | null>(null)

  // Only used to hide Remove on the admin's own row. If this fails, the
  // button shows and user-service still rejects self-removal.
  useEffect(() => {
    let cancelled = false
    adminUserApi.getCurrentUser().then(
      (me) => {
        if (!cancelled) setCurrentUserId(me.id)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [])

  // Apply the search box to the query once typing pauses.
  useEffect(() => {
    if (searchInput === search) return
    const timer = setTimeout(() => {
      setSearch(searchInput)
      setPage(1)
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput, search])

  const trimmedSearch = search.trim()
  const queryKey = JSON.stringify([trimmedSearch, role, page, reloadCount])

  useEffect(() => {
    let cancelled = false

    adminUserApi
      .listUsers({
        search: trimmedSearch || undefined,
        role: role || undefined,
        page: page - 1,
        size: USERS_PAGE_SIZE,
      })
      .then(
        (data) => {
          if (!cancelled) setResult({ key: queryKey, data, error: null })
        },
        (err: unknown) => {
          if (!cancelled)
            setResult({
              key: queryKey,
              data: null,
              error: errorMessage(err, 'Could not load users. Try again.'),
            })
        },
      )

    return () => {
      cancelled = true
    }
  }, [queryKey, trimmedSearch, role, page])

  const loading = result?.key !== queryKey
  const data = result?.data ?? null
  const error = actionError ?? (loading ? null : (result?.error ?? null))

  function handleRoleFilter(value: UserRole | '') {
    setRole(value)
    setPage(1)
  }

  function updateUsers(update: (users: AdminUser[]) => AdminUser[]) {
    setResult((prev) =>
      prev?.data
        ? {
            ...prev,
            data: { ...prev.data, content: update(prev.data.content) },
          }
        : prev,
    )
  }

  async function handleChangeRole() {
    if (!pendingRoleChange) return
    const { user, role: newRole } = pendingRoleChange
    setBusyUserId(user.id)
    setActionError(null)
    try {
      const updated = await adminUserApi.changeRole(user.id, newRole)
      updateUsers((users) =>
        users.map((u) => (u.id === updated.id ? updated : u)),
      )
      // The user may no longer match the role filter, and counts change.
      setReloadCount((n) => n + 1)
    } catch (err) {
      setActionError(
        errorMessage(err, `Could not change ${user.username}'s role.`),
      )
    } finally {
      setPendingRoleChange(null)
      setBusyUserId(null)
    }
  }

  async function handleRemove() {
    if (!pendingRemove) return
    const target = pendingRemove
    setBusyUserId(target.id)
    setActionError(null)
    try {
      await adminUserApi.removeUser(target.id)
      // Removed rows aren't refetched: GET /users still returns
      // soft-deleted accounts. Removing a later page's last row goes back
      // a page instead of showing an empty one.
      if (data?.content.length === 1 && page > 1) {
        setPage(page - 1)
      } else {
        updateUsers((users) => users.filter((u) => u.id !== target.id))
      }
    } catch (err) {
      setActionError(errorMessage(err, `Could not remove ${target.username}.`))
    } finally {
      setPendingRemove(null)
      setBusyUserId(null)
    }
  }

  const filtered = trimmedSearch !== '' || role !== ''

  return (
    <section className="space-y-4" aria-labelledby="users-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 id="users-heading" className="text-lg font-semibold text-gray-900">
          Users
        </h2>
        <div className="flex flex-col gap-2 sm:flex-row">
          <input
            type="search"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by ID, username or email..."
            aria-label="Search by ID, username or email"
            className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:border-gray-400 focus:outline-none sm:w-64"
          />
          <select
            value={role}
            onChange={(e) => handleRoleFilter(e.target.value as UserRole | '')}
            aria-label="Filter by role"
            className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm focus:border-gray-400 focus:outline-none sm:w-36"
          >
            {roleOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && (
        <p
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}

      {/* While a new query loads, the previous results stay on screen. */}
      {!data ? (
        loading && <p className="text-sm text-gray-500">Loading users...</p>
      ) : data.content.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
          <p className="font-medium text-gray-900">
            {filtered
              ? 'No users match your search or filter.'
              : 'No users yet.'}
          </p>
        </div>
      ) : (
        <>
          <UserTable
            users={data.content}
            currentUserId={currentUserId}
            busyUserId={busyUserId}
            onChangeRole={(user, newRole) =>
              setPendingRoleChange({ user, role: newRole })
            }
            onRemove={setPendingRemove}
          />
          <Pagination
            page={page}
            totalPages={data.page.totalPages}
            onPageChange={setPage}
            disabled={loading}
          />
        </>
      )}

      {pendingRoleChange && (
        <ChangeRoleModal
          user={pendingRoleChange.user}
          role={pendingRoleChange.role}
          isSelf={pendingRoleChange.user.id === currentUserId}
          onCancel={() => setPendingRoleChange(null)}
          onConfirm={handleChangeRole}
          saving={busyUserId === pendingRoleChange.user.id}
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
    </section>
  )
}
