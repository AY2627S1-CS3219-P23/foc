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
// hook re-resolves on a tick while mounted, so a failed lookup recovers
// once its negative TTL lapses (and a rename lands once the positive
// TTL does) — previously nothing re-asked the cache until the id set
// changed. Ticks hit the cache, not the network, inside a TTL.
// 2026-10-19, Claude Code (Fable 5), PR #222 review (@Sinnez1): a
// definite 404 now clears a name the hook already surfaced (deleted
// user mid-mount), while transient failures keep the last known name;
// the tick runs independently of the id set (keying it on the ids
// restarted the countdown on every push, muting recovery); expiry is
// stamped from request issue and the tick runs at half the negative
// TTL, so a retry lands within one TTL instead of slipping a period.

import { ApiError, apiFetch } from '@/lib/api/http'
import { useEffect, useMemo, useState } from 'react'

const POSITIVE_TTL_MS = 10 * 60_000
const NEGATIVE_TTL_MS = 30_000
// Half the shortest TTL: entries expire relative to request-issue time,
// which the ticks are phase-locked to, so a tick equal to the TTL would
// land right on the expiry boundary and a retry could slip a whole
// period (~60 s for a 30 s promise — PR #222 review).
const TICK_MS = NEGATIVE_TTL_MS / 2
// crude bound, like the retired backend adapter's: a runaway id space
// resets the cache, not the tab's memory
const MAX_CACHE_ENTRIES = 10_000

interface Lookup {
  value: string | null
  // true when the server definitively answered 404 — the user is gone,
  // as opposed to a transient failure (network, 5xx) where the last
  // known name is still the best guess (PR #222 review)
  missing: boolean
}

interface Entry extends Lookup {
  expiresAt: number
}

const cache = new Map<string, Entry>()
const inFlight = new Map<string, Promise<Lookup>>()

/** Test hook: module state would otherwise bleed across vitest files. */
export function clearUsernameCache(): void {
  cache.clear()
  inFlight.clear()
}

// The resolver behind the public surfaces: answers from cache inside a
// TTL, dedupes in-flight requests, and never rejects.
function resolve(id: string): Promise<Lookup> {
  // issue time, also the TTL epoch: stamping expiry at settle time
  // (issue + latency) put it just past the hook's phase-locked tick,
  // deferring every retry by a full period (PR #222 review)
  const now = Date.now()
  const cached = cache.get(id)
  if (cached && now < cached.expiresAt) {
    return Promise.resolve({ value: cached.value, missing: cached.missing })
  }
  const pending = inFlight.get(id)
  if (pending) return pending

  const request = apiFetch<{ username: string }>(
    'user',
    `/users/${encodeURIComponent(id)}`,
  )
    .then((profile): Lookup => ({
      value:
        typeof profile.username === 'string' && profile.username !== ''
          ? profile.username
          : null,
      missing: false,
    }))
    .catch((err: unknown): Lookup => ({
      value: null,
      missing: err instanceof ApiError && err.status === 404,
    }))
    .then((lookup) => {
      if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
      cache.set(id, {
        ...lookup,
        expiresAt:
          now + (lookup.value === null ? NEGATIVE_TTL_MS : POSITIVE_TTL_MS),
      })
      return lookup
    })
    .finally(() => {
      inFlight.delete(id)
    })
  inFlight.set(id, request)
  return request
}

/**
 * Resolves one user id to a username, or null when it can't — unknown
 * or deleted user (404), bad id (400), network failure, anything.
 * Never throws; misses are negative-cached briefly so a burst of rows
 * naming an unresolvable user doesn't hammer the gateway.
 */
export function resolveUsername(id: string): Promise<string | null> {
  return resolve(id).then((lookup) => lookup.value)
}

/**
 * Hydrates a list of user ids into id → username. Entries appear as
 * lookups settle (cache hits settle in a microtask); ids that can't
 * resolve render their callers' no-name copy, and a definite 404 for an
 * id that had resolved (user deleted mid-mount) drops its entry again.
 * While mounted the hook re-resolves every {@link TICK_MS}, so a failed
 * lookup is retried once its negative cache lapses and a rename shows
 * once the positive TTL does — inside a TTL the tick is a cache hit,
 * no network.
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
    // deliberately independent of the id set: keying the interval on it
    // restarted the countdown on every push, so with frames arriving
    // under a tick apart a failed lookup never retried (PR #222 review)
    const timer = setInterval(() => setAttempt((n) => n + 1), TICK_MS)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (key === '') return
    let cancelled = false
    for (const id of key.split('\u0000')) {
      void resolve(id).then(({ value, missing }) => {
        if (cancelled) return
        setNames((prev) => {
          if (value === null) {
            // only a definite 404 clears a surfaced name — a transient
            // failure keeps the last known one rather than flickering
            // the row back to actor-less copy (PR #222 review)
            if (!missing || !prev.has(id)) return prev
            const next = new Map(prev)
            next.delete(id)
            return next
          }
          if (prev.get(id) === value) return prev
          const next = new Map(prev)
          next.set(id, value)
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
