// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (credit balances and Add Credits removed; wired to the #96
// endpoints with server-side search, role filter and paging; PR #140
// review: reload after a role change, step back a page when its last
// row is removed, and GET /users/me so the own row has no Remove;
// promote/demote now asks for confirmation first; second PR #140 review:
// removals reload (GET /users now hides soft-deleted accounts), a page past
// the end moves to the last page, the error banner clears on a new query,
// search is trimmed before debouncing, the fetch lists its inputs, and an
// unknown signed-in user gets a general self-demotion warning).
// Scope: Admin Dashboard "Users" section — list, search, role filter,
// paging, promote/demote, remove — per
// web/docs/wireframes/admin-dashboard.png.
// 2026-09-29, Claude Code (Fable 5), issue #112: local errorMessage
// replaced by the copy hoisted to lib/api/http.ts (that variant shows
// the fallback for TypeErrors, i.e. network failures, instead of the
// browser's terse message — a small behavior improvement).
// Reviewed by: Ryan Ang

import { useEffect, useState } from 'react'

import { errorMessage } from '@/lib/api/http'
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

// Identifies one list request, so a newer query shows as loading until its
// own result arrives.
function queryKeyOf(
  search: string,
  role: UserRole | '',
  page: number,
  reloadCount: number,
) {
  return JSON.stringify([search, role, page, reloadCount])
}

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
  // null until GET /users/me answers, and if it fails
  const [currentUserId, setCurrentUserId] = useState<number | null>(null)

  // Used to hide Remove on the admin's own row and to warn before
  // self-demotion. If it fails, Remove shows (user-service still rejects
  // self-removal) and every demotion gets a general warning instead.
  useEffect(() => {
    let cancelled = false
    adminUserApi.getCurrentUser().then(
      (me) => {
        if (!cancelled) setCurrentUserId(me.id)
      },
      (err: unknown) => {
        console.warn('Could not identify the signed-in admin', err)
      },
    )
    return () => {
      cancelled = true
    }
  }, [])

  // Apply the search box to the query once typing pauses. Compared
  // trimmed, so a whitespace-only edit doesn't reset the page.
  useEffect(() => {
    const trimmed = searchInput.trim()
    if (trimmed === search) return
    const timer = setTimeout(() => {
      setSearch(trimmed)
      setPage(1)
      setActionError(null)
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput, search])

  useEffect(() => {
    let cancelled = false
    const key = queryKeyOf(search, role, page, reloadCount)

    adminUserApi
      .listUsers({
        search: search || undefined,
        role: role || undefined,
        page: page - 1,
        size: USERS_PAGE_SIZE,
      })
      .then(
        (data) => {
          if (cancelled) return
          // Past the last page (a role change or removal emptied it): go to
          // the last page instead, keeping the old rows until it loads.
          const lastPage = Math.max(1, data.page.totalPages)
          if (page > lastPage) {
            setPage(lastPage)
            return
          }
          setResult({ key, data, error: null })
        },
        (err: unknown) => {
          if (!cancelled)
            setResult({
              key,
              data: null,
              error: errorMessage(err, 'Could not load users. Try again.'),
            })
        },
      )

    return () => {
      cancelled = true
    }
  }, [search, role, page, reloadCount])

  const loading = result?.key !== queryKeyOf(search, role, page, reloadCount)
  const data = result?.data ?? null
  const error = actionError ?? (loading ? null : (result?.error ?? null))

  // A new query starts without the last action's error.
  function handleRoleFilter(value: UserRole | '') {
    setRole(value)
    setPage(1)
    setActionError(null)
  }

  function handlePageChange(value: number) {
    setPage(value)
    setActionError(null)
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
      // Hide the row now, unless it's the page's last: then the reload's
      // last-page check moves back a page without flashing an empty list
      if ((data?.content.length ?? 0) > 1) {
        updateUsers((users) => users.filter((u) => u.id !== target.id))
      }
      // GET /users leaves removed accounts out, so reload for the right
      // counts
      setReloadCount((n) => n + 1)
    } catch (err) {
      setActionError(errorMessage(err, `Could not remove ${target.username}.`))
    } finally {
      setPendingRemove(null)
      setBusyUserId(null)
    }
  }

  const filtered = search !== '' || role !== ''

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
            onPageChange={handlePageChange}
            disabled={loading}
          />
        </>
      )}

      {pendingRoleChange && (
        <ChangeRoleModal
          user={pendingRoleChange.user}
          role={pendingRoleChange.role}
          isSelf={
            currentUserId === null
              ? null
              : pendingRoleChange.user.id === currentUserId
          }
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
