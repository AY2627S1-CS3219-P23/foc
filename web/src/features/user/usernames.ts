// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-19, issue #221.
// Scope: read-time username hydration per the author's D32 decision
// (options Q&A, supersedes backend D30/D31): notification payloads carry
// party IDs only, and the browser resolves them against user-service's
// public-profile route through the gateway with the caller's own JWT —
// no service-to-service call, no internal token. Cache TTLs mirror the
// retired ingest-side adapter (10 min positive / 30 s negative), per-id
// requests are deduped in flight, and every failure degrades to "no
// name" so the templates' actor-less copy renders — never an error.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-19, Claude Code (Fable 5), PR #222 review (@claude bot): the
// hook re-resolves on a 30 s tick while mounted, so a failed lookup
// recovers once its negative TTL lapses (and a rename lands once the
// positive TTL does) — previously nothing re-asked the cache until the
// id set changed. Ticks hit the cache, not the network, inside a TTL.

import { apiFetch } from '@/lib/api/http'
import { useEffect, useMemo, useState } from 'react'

const POSITIVE_TTL_MS = 10 * 60_000
const NEGATIVE_TTL_MS = 30_000
// crude bound, like the retired backend adapter's: a runaway id space
// resets the cache, not the tab's memory
const MAX_CACHE_ENTRIES = 10_000

interface Entry {
  value: string | null
  expiresAt: number
}

const cache = new Map<string, Entry>()
const inFlight = new Map<string, Promise<string | null>>()

/** Test hook: module state would otherwise bleed across vitest files. */
export function clearUsernameCache(): void {
  cache.clear()
  inFlight.clear()
}

/**
 * Resolves one user id to a username, or null when it can't — unknown
 * or deleted user (404), bad id (400), network failure, anything.
 * Never throws; misses are negative-cached briefly so a burst of rows
 * naming an unresolvable user doesn't hammer the gateway.
 */
export function resolveUsername(id: string): Promise<string | null> {
  const now = Date.now()
  const cached = cache.get(id)
  if (cached && now < cached.expiresAt) return Promise.resolve(cached.value)
  const pending = inFlight.get(id)
  if (pending) return pending

  const request = apiFetch<{ username: string }>(
    'user',
    `/users/${encodeURIComponent(id)}`,
  )
    .then((profile) =>
      typeof profile.username === 'string' && profile.username !== ''
        ? profile.username
        : null,
    )
    .catch(() => null)
    .then((value) => {
      if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
      cache.set(id, {
        value,
        expiresAt:
          Date.now() + (value === null ? NEGATIVE_TTL_MS : POSITIVE_TTL_MS),
      })
      return value
    })
    .finally(() => {
      inFlight.delete(id)
    })
  inFlight.set(id, request)
  return request
}

/**
 * Hydrates a list of user ids into id → username. Entries appear as
 * lookups settle (cache hits settle in a microtask); ids that can't
 * resolve render their callers' no-name copy. While mounted the hook
 * re-resolves every {@link NEGATIVE_TTL_MS}, so a failed lookup is
 * retried once its negative cache lapses and a rename shows once the
 * positive TTL does — inside a TTL the tick is a cache hit, no network.
 */
export function useUsernames(
  ids: ReadonlyArray<string | null>,
): ReadonlyMap<string, string> {
  // dedupe + sort so re-renders with the same ids in a different order
  // or multiplicity don't re-run the effect ('\0' can't appear in ids
  // on any realistic payload; a stray one only costs a spurious re-run)
  const key = useMemo(() => {
    const unique = [...new Set(ids.filter((id): id is string => id !== null))]
    return unique.sort().join('\u0000')
  }, [ids])

  const [names, setNames] = useState<ReadonlyMap<string, string>>(new Map())
  // bumped on an interval to re-run the resolve effect (PR #222 review:
  // keying on the id set alone muted recovery until the ids changed)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (key === '') return
    const timer = setInterval(() => setAttempt((n) => n + 1), NEGATIVE_TTL_MS)
    return () => clearInterval(timer)
  }, [key])

  useEffect(() => {
    if (key === '') return
    let cancelled = false
    for (const id of key.split('\u0000')) {
      void resolveUsername(id).then((name) => {
        if (cancelled || name === null) return
        setNames((prev) => {
          if (prev.get(id) === name) return prev
          const next = new Map(prev)
          next.set(id, name)
          return next
        })
      })
    }
    return () => {
      cancelled = true
    }
  }, [key, attempt])

  return names
}
