// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude, 2026-09-22; revised 2026-09-26 (Claude Code, Sonnet 5).
// Scope: Suppliers page (browse/search/filter + admin CRUD), per
// web/docs/wireframes/suppliers.png and add-edit-supplier.png.
// Implements F1.2, F1.2.1, F2.1, F2.2, F2.2.1, F1.1-F1.1.4.
//
// 2026-10-05, Claude Code (Sonnet 5), issue #160: the category filter
// list is re-fetched on refreshKey, so a category added by a create or
// update appears in the dropdown without a page reload.
//
// 2026-09-29, Claude Code (Sonnet 5): the hardcoded IS_ADMIN constant
// (a TEMPORARY stand-in noted here since auth didn't exist yet) is
// replaced with useAuth()'s real role — supplier-service's role gate
// (issue #106) already rejects a non-admin's CRUD calls server-side,
// but this page showed Add/Edit/Delete to everyone regardless, so a
// regular user could click into a control that was always going to
// 403. Now those controls only render for role === 'ADMIN'.
// PR #143 review (LeongWZ): OWNER can't do supplier CRUD as written —
// Role.OWNER (issue #97) is the platform's admin-equivalent super
// admin, so the backend gate now allows ADMIN or OWNER; this check
// updated to match (isAdmin renders for either role).
// 2026-09-29, Claude Code (Sonnet 5), issue #104: added a green success
// banner (create/update/delete), matching the existing red error
// banner's style, auto-dismissed after 4s; mutually exclusive with the
// error banner (each clears the other on set).
//
// 2026-09-26 (issue #133): wired to the real, now-paginated
// GET /suppliers. Removed the `listZones()` call — the backend has no
// /zones endpoint (team dropped the zone feature) and calling it was
// throwing inside Promise.all, breaking this page's initial load
// entirely. Added page state + Prev/Next controls for the D2
// "paginating through supplier data" UI requirement; page resets to 0
// whenever the search/category filters change.
// 2026-09-26: removed the zone plumbing entirely (state, props passed
// to SupplierFilterBar/SupplierFormModal/SupplierDetailPanel) — the
// empty, required Campus Zone <select> in the edit form could never
// pass validation with no zones to populate it, blocking every edit.
// 2026-09-26: fetch the category-filter options from the new
// GET /suppliers/categories endpoint instead of deriving them from the
// current (filtered, paginated) `suppliers` state — the derived list
// was a bug: filtering to "Food" then reopening the dropdown only
// offered categories present on Food suppliers, not every category.
// 2026-09-27: added a sort control, including "Nearest to Me" (team
// decision) which requests the browser's Geolocation API and, once
// granted, passes lat/lng to GET /suppliers so the backend orders by
// distance. On denial/error, falls back to name sort with a notice
// rather than leaving the page stuck loading. Distance labels on each
// card are computed client-side (features/supplier/distance.ts) from
// the same coordinates already in the response — display only; the
// authoritative order comes from the backend.
// 2026-09-27 (PR #134 Copilot review): handleSave/handleDelete used to
// patch the local `suppliers` array directly (append on create, filter
// on delete) without touching totalElements/totalPages — harmless
// before pagination existed (the array held every matching supplier),
// but a real bug once `suppliers` is just one page's slice: creating
// on a full page silently grew it past PAGE_SIZE, and deleting the
// last item on the last page left stale "Page N of N" pagination text
// pointing at a page that no longer exists. Fixed by extracting the
// fetch into fetchCurrentPage() and calling it after a successful
// mutation instead of hand-patching state, so the list and its
// pagination metadata always come from the same server response.
// 2026-09-28 (PR #134 review, LeongWZ): getCurrentPosition had no
// `timeout` — the spec default is Infinity, so a user who dismissed or
// ignored the browser's permission prompt (rather than explicitly
// allowing or denying it) left neither callback ever firing.
// `userLocation` stayed null forever, `fetchCurrentPage` kept
// resolving to null, and the page silently kept showing the previous
// (stale) sorted-by-name results under a "Nearest to Me" label with no
// spinner or notice — and, worse, this state made handleSave/handleDelete's
// `if (result)` guard skip the post-mutation refresh entirely,
// reintroducing the exact stale-list bug fixed above through a
// different path. Fixed by passing a `timeout` (falls into the
// existing, already-correct error callback after the timeout) and adding
// `waitingForLocation`, a derived render-time flag (not stored state —
// it's fully computed from sort/userLocation already in scope) that
// shows an explicit "Waiting for your location…" state instead of
// silently rendering stale data during the wait.
// 2026-09-28: added a JS-level backstop timer alongside the native
// `timeout` option — observed in practice (Chrome/Windows, incognito)
// that the native timeout isn't always honored when the browser's
// underlying call to the OS's location service itself hangs; the
// JS-level option only preempts the browser's own internal logic, not
// a blocked native call underneath it. An independent `setTimeout`
// guarantees the fallback fires after the timeout regardless of
// whether the browser's own timeout does.
// 2026-09-28: shortened the timeout from 10s to 3s (author decision) —
// faster feedback for the user when location can't be obtained.
// 2026-09-28 (PR #134 review, LeongWZ): handleSave/handleDelete used
// to call fetchCurrentPage() and set suppliers/totalPages/totalElements
// themselves — a third copy of that logic (alongside the load effect),
// with two problems: (1) it shared one try/catch with the actual
// mutation, so a refetch failure after a successful save was reported
// as "Could not save supplier," and (2) unlike the load effect it had
// no `cancelled` staleness guard, so an in-flight post-mutation
// refetch could resolve after a newer, filter-changed request and
// overwrite it with stale data. Fixed by having both handlers just
// bump `refreshKey`, an effect dependency with no other purpose, so
// the refresh runs through the load effect itself and inherits its
// existing guard and error handling instead of duplicating them.
// 2026-09-28 (PR #134 review, LeongWZ): "Nearest to Me" could wedge the
// page permanently in a browser without geolocation support — see the
// comment above handleSortChange for the mechanism. Fixed by rejecting
// the sort selection in that handler instead of correcting `sort` back
// as a render-phase side effect.
// 2026-09-29 (Claude Code, Opus 5.5, issue #147): the hand-rolled
// Previous/Next pager replaced by the shared Pagination component (so it
// gains page numbers and aria-current), keeping the "Page X of Y · N
// suppliers" label beside it; load/save/delete errors use the shared
// errorMessage helper.
// Reviewed by: Ryan Ang (the 2026-09-29 issue #147 changes above); the
// original supplier code's review is still pending with its author.

import { Fragment, useCallback, useEffect, useState } from 'react'

import {
  createSupplier,
  deleteSupplier,
  listCategories,
  listSuppliers,
  updateSupplier,
} from '@/features/supplier/api'
import { DeleteSupplierModal } from '@/features/supplier/components/DeleteSupplierModal'
import { SupplierCard } from '@/features/supplier/components/SupplierCard'
import { SupplierDetailPanel } from '@/features/supplier/components/SupplierDetailPanel'
import { SupplierFilterBar } from '@/features/supplier/components/SupplierFilterBar'
import { SupplierFormModal } from '@/features/supplier/components/SupplierFormModal'
import { formatDistance, haversineDistanceMeters } from '@/features/supplier/distance'
import type { Supplier, SupplierInput } from '@/features/supplier/types'
import { errorMessage } from '@/lib/api/http'
import { Pagination } from '@/shared/components/Pagination'
import { useAuth } from '@/features/user/useAuth'

const PAGE_SIZE = 10
const DISTANCE_SORT = 'distance'

interface Coordinates {
  lat: number
  lng: number
}

export function Suppliers() {
  const { role } = useAuth()
  const isAdmin = role === 'ADMIN' || role === 'OWNER'

  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [sort, setSort] = useState('name,asc')
  const [userLocation, setUserLocation] = useState<Coordinates | null>(null)
  const [locationNotice, setLocationNotice] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  // Bumped by handleSave/handleDelete to re-trigger the load effect
  // below after a mutation — see that effect's comment for why.
  const [refreshKey, setRefreshKey] = useState(0)

  const [formOpenFor, setFormOpenFor] = useState<Supplier | 'new' | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Supplier | null>(null)
  const [saving, setSaving] = useState(false)

  // Filter changes should always jump back to the first page — a
  // stale page number from a previous, larger result set would
  // otherwise silently show an empty page. Reset during render (React's
  // recommended pattern for "adjust state when a prop changes") rather
  // than in an effect, which would cause an extra render pass.
  const [prevFilters, setPrevFilters] = useState({ query, category, sort })
  if (query !== prevFilters.query || category !== prevFilters.category || sort !== prevFilters.sort) {
    setPrevFilters({ query, category, sort })
    setPage(0)
  }

  // 2026-09-28 (PR #134 review, LeongWZ): checking geolocation support
  // and correcting `sort` back to name-sort during render (the previous
  // approach) only fires once, because it self-gates on `!locationNotice`
  // — the very thing it sets. Selecting "Nearest to Me" again in a
  // browser without geolocation support left `locationNotice` already
  // set, so the guard's condition no longer matched and `sort` stayed
  // stuck at 'distance' with `userLocation` never set, which wedges
  // fetchCurrentPage() (below) into resolving null forever — the list
  // stops updating on any further filter/search change, silently.
  // Fixed by rejecting the selection in the change handler itself
  // instead: 'distance' is only ever committed to `sort` when
  // geolocation is actually supported, so this notice-setting can run
  // every time the user picks "Nearest to Me", not just the first.
  function handleSortChange(value: string) {
    if (value === DISTANCE_SORT && !navigator.geolocation) {
      setLocationNotice('Location is not supported by this browser — showing suppliers sorted by name instead.')
      return
    }
    setSort(value)
  }

  // "Nearest to Me" needs a coordinate before it can fetch anything —
  // request it only when that sort is actually selected (never
  // proactively), and fall back to name sort with a visible notice if
  // geolocation fails. The effect's only synchronous action is
  // starting the browser's async geolocation call; both setState calls
  // happen inside its callbacks, not directly in the effect body.
  useEffect(() => {
    if (sort !== DISTANCE_SORT || userLocation || !navigator.geolocation) return

    // `settled` guards against a double-resolution: either the native
    // callback and the backstop timer both firing (whichever wins
    // first should be the only one that takes effect), or a late
    // native callback arriving after this effect has already been
    // cleaned up (e.g. the user switched sort again in the meantime).
    let settled = false

    function handleFailure() {
      if (settled) return
      settled = true
      // GeolocationPositionError has three distinct codes (denied,
      // unavailable, timeout) — deliberately not distinguishing them
      // here (author decision): the fallback behavior is identical
      // either way, and a message like "permission denied" would be
      // an outright wrong claim when the real cause is something
      // else (e.g. incognito mode blocking location entirely, or the
      // OS's own location toggle being off) rather than the user
      // having denied anything.
      setLocationNotice("Couldn't get your location — showing suppliers sorted by name instead.")
      setSort('name,asc')
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (settled) return
        settled = true
        setUserLocation({ lat: position.coords.latitude, lng: position.coords.longitude })
        setLocationNotice(null)
      },
      handleFailure,
      // Without a timeout, an unanswered (dismissed/ignored, not
      // explicitly denied) permission prompt never fires either
      // callback — the spec default is Infinity.
      { timeout: 3000 },
    )

    // Backstop: the native `timeout` option above isn't reliably
    // honored by every browser/OS combination — observed in practice
    // on Chrome/Windows, where a stuck call down to the OS's own
    // location service can hang past the requested timeout, since the
    // JS-level option only preempts the browser's own internal logic,
    // not a blocked native call underneath it. This independent timer
    // guarantees the fallback fires regardless of whether the browser
    // honors its own timeout.
    const backstop = window.setTimeout(handleFailure, 3000)

    return () => {
      settled = true
      window.clearTimeout(backstop)
    }
  }, [sort, userLocation])

  // What the current page's request looks like, given the active
  // filters/sort/location. Only ever called from the effect below.
  const fetchCurrentPage = useCallback(() => {
    if (sort === DISTANCE_SORT && !userLocation) {
      // Waiting on the geolocation callback above — nothing to fetch yet.
      return Promise.resolve(null)
    }
    return sort === DISTANCE_SORT && userLocation
      ? listSuppliers({
          search: query,
          category,
          page,
          size: PAGE_SIZE,
          lat: userLocation.lat,
          lng: userLocation.lng,
        })
      : listSuppliers({ search: query, category, page, size: PAGE_SIZE, sort })
  }, [query, category, page, sort, userLocation])

  // The single source of truth for "what does the current page look
  // like" — reacts to filter/sort/location changes directly, and to a
  // create/update/delete indirectly via `refreshKey` (bumped by
  // handleSave/handleDelete rather than those handlers calling
  // fetchCurrentPage themselves). Centralizing the refetch here means:
  // (1) it inherits this effect's own `cancelled` guard, so a stale
  // response from before a filter change can no longer overwrite a
  // newer one — handleSave/handleDelete previously called
  // fetchCurrentPage() directly with no such protection; and (2) a
  // mutation's own try/catch no longer wraps this fetch too, so a
  // failure here can't be misattributed as "the save/delete failed"
  // when the write actually succeeded and only this unrelated
  // read-back failed (PR #134 review, LeongWZ).
  useEffect(() => {
    let cancelled = false

    async function loadSuppliers() {
      setLoading(true)
      setError(null)
      try {
        const result = await fetchCurrentPage()
        if (cancelled || !result) return
        setSuppliers(result.content)
        setTotalPages(result.totalPages)
        setTotalElements(result.totalElements)
      } catch (err) {
        if (cancelled) return
        setError(errorMessage(err, 'Could not load suppliers. Try again.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadSuppliers()

    return () => {
      cancelled = true
    }
  }, [fetchCurrentPage, refreshKey])

  // Independent of the current search/category filter — this must always
  // offer every category that exists, not just those present on the
  // currently filtered/paginated suppliers. Re-fetched on refreshKey too,
  // so a category added by a create/update shows up without a reload.
  useEffect(() => {
    let cancelled = false

    listCategories()
      .then((result) => {
        if (!cancelled) setCategories(result)
      })
      .catch(() => {
        // Non-fatal: the filter dropdown just falls back to "All" only.
      })

    return () => {
      cancelled = true
    }
  }, [refreshKey])

  // Auto-dismisses a create/update/delete success banner after a few
  // seconds — errors stay until the next action, but a success message
  // that lingers just clutters the page once the user has moved on.
  useEffect(() => {
    if (!successMessage) return
    const timeout = window.setTimeout(() => setSuccessMessage(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [successMessage])

  const selected = suppliers.find((s) => s.id === selectedId) ?? null

  // Derived, not stored — while true, fetchCurrentPage resolves to
  // null (nothing to show yet), so the list below must not render
  // whatever `suppliers` was left over from before "Nearest to Me"
  // was selected.
  const waitingForLocation = sort === DISTANCE_SORT && !userLocation

  async function handleSave(input: SupplierInput) {
    const isEdit = formOpenFor !== 'new'
    setSaving(true)
    try {
      if (formOpenFor && formOpenFor !== 'new') {
        await updateSupplier(formOpenFor.id, input)
      } else {
        await createSupplier(input)
      }
      setFormOpenFor(null)
      setError(null)
      setSuccessMessage(
        isEdit
          ? `Successfully updated Supplier "${input.name}".`
          : `Successfully created Supplier "${input.name}".`,
      )
      // Trigger the load effect rather than fetching and setting state
      // here directly — see that effect's comment for why (staleness
      // guard, and not misattributing a refetch failure as "the save
      // failed").
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setSuccessMessage(null)
      setError(errorMessage(err, 'Could not save supplier.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return
    setSaving(true)
    try {
      await deleteSupplier(pendingDelete.id)
      if (selectedId === pendingDelete.id) setSelectedId(null)
      setPendingDelete(null)
      setError(null)
      setSuccessMessage(`Successfully deleted Supplier "${pendingDelete.name}".`)
      // Deleting the last item on the last page would otherwise leave
      // `page` pointing past the new totalPages — step back a page
      // first when that happens; `page` is already one of
      // fetchCurrentPage's own dependencies, so that alone re-triggers
      // the load effect without also bumping refreshKey.
      if (page > 0 && suppliers.length === 1) {
        setPage((p) => p - 1)
      } else {
        setRefreshKey((k) => k + 1)
      }
    } catch (err) {
      setSuccessMessage(null)
      setError(errorMessage(err, 'Could not delete supplier.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-xl font-semibold text-gray-900">Active Campus Suppliers</h1>
        {isAdmin && (
          <button
            type="button"
            onClick={() => setFormOpenFor('new')}
            className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
          >
            + Add Supplier
          </button>
        )}
      </div>

      <SupplierFilterBar
        query={query}
        onQueryChange={setQuery}
        category={category}
        onCategoryChange={setCategory}
        categories={categories}
        sort={sort}
        onSortChange={handleSortChange}
        locationNotice={locationNotice}
      />

      {successMessage && (
        <p className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
          {successMessage}
        </p>
      )}

      {error && (
        <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className={`grid gap-6 ${selected ? 'lg:grid-cols-[1fr_20rem]' : ''}`}>
        <div>
          {loading || waitingForLocation ? (
            <p className="text-sm text-gray-500">
              {waitingForLocation ? 'Waiting for your location…' : 'Loading suppliers...'}
            </p>
          ) : suppliers.length === 0 ? (
            <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
              <p className="font-medium text-gray-900">
                No other suppliers match your filters.
              </p>
              <p className="mt-1 text-sm text-gray-500">
                Try adjusting your search query or category filter.
              </p>
            </div>
          ) : (
            <div className="grid grid-flow-dense gap-3 sm:grid-cols-2">
              {suppliers.map((supplier) => (
                <Fragment key={supplier.id}>
                  <SupplierCard
                    supplier={supplier}
                    selected={supplier.id === selectedId}
                    onSelect={() =>
                      setSelectedId(supplier.id === selectedId ? null : supplier.id)
                    }
                    distanceLabel={
                      sort === DISTANCE_SORT && userLocation
                        ? formatDistance(
                            haversineDistanceMeters(
                              userLocation.lat,
                              userLocation.lng,
                              supplier.latitude,
                              supplier.longitude,
                            ),
                          )
                        : undefined
                    }
                  />
                  {/* Below lg, the sidebar column collapses away, so show
                      the panel inline right after its card instead of
                      letting it fall to the end of the grid. */}
                  {selected && supplier.id === selectedId && (
                    <div className="col-span-full lg:hidden">
                      <SupplierDetailPanel
                        supplier={selected}
                        isAdmin={isAdmin}
                        onEdit={() => setFormOpenFor(selected)}
                        onDelete={() => setPendingDelete(selected)}
                      />
                    </div>
                  )}
                </Fragment>
              ))}
            </div>
          )}

          {!loading && !waitingForLocation && totalPages > 1 && (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
              <span>
                Page {page + 1} of {totalPages} · {totalElements} suppliers
              </span>
              {/* The shared pager is 1-based; this page's state is 0-based
                  like the API. */}
              <Pagination
                page={page + 1}
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)}
              />
            </div>
          )}
        </div>

        {/* Desktop: side panel column. */}
        {selected && (
          <div className="hidden lg:block">
            <SupplierDetailPanel
              supplier={selected}
              isAdmin={isAdmin}
              onEdit={() => setFormOpenFor(selected)}
              onDelete={() => setPendingDelete(selected)}
            />
          </div>
        )}
      </div>

      {formOpenFor && (
        <SupplierFormModal
          initial={formOpenFor === 'new' ? undefined : formOpenFor}
          onCancel={() => setFormOpenFor(null)}
          onSave={handleSave}
          saving={saving}
        />
      )}

      {pendingDelete && (
        <DeleteSupplierModal
          supplier={pendingDelete}
          onCancel={() => setPendingDelete(null)}
          onConfirm={handleDelete}
          deleting={saving}
        />
      )}
    </div>
  )
}
