// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, PR #142 (JWT wiring into
// apiFetch). Scope: the return type is now the stored T | null instead
// of jose's JWTPayload, which the hook never actually produced; storage
// behaviour unchanged. The hook itself comes from PR #139.
// 2026-09-29, Claude Code (Opus 5.5), issue #147: Prettier formatting
// only.
// 2026-10-14, Claude Code (Opus 5), PR #182 review (@Sinnez1):
// readStoredValue added so a caller can see what storage holds *now* —
// state captured at the last committed render can be out of date by a
// render, or by another tab's write (the key is shared). The hook itself
// is unchanged.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-18, Claude Code (Fable 5), notification sound (follow-up to
// issue #66, no issue yet): setValue memoized with useCallback —
// NotificationProvider now passes it through a useMemo'd context value,
// which a per-render function identity would defeat. Behaviour
// unchanged for existing callers.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@Sinnez1): the
// hook now hears another tab's write of its key (storage events fire
// only in the OTHER tabs), so a device-wide preference like the
// notification sound lands everywhere without a reload. This also
// means a session stored under 'user' in one tab reaches the others
// live — a logout signs every tab out — which is the fitting reading
// of a shared key.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@claude bot): the
// cross-tab listener became opt-in ({ sync: true }, used by the sound
// preference only) — hook-wide it silently changed the 'user' session
// key too, swapping tab A's session live when tab B logs in, a
// behaviour change beyond this PR with no test of its own. The
// defaultValue the listener falls back to is read through a ref, so an
// object-literal default cannot re-subscribe every render.

import { useCallback, useEffect, useRef, useState } from 'react'

// what the key holds right now, parsed; null when unset or unparseable.
// Unlike the hook's state this is never stale: setValue below writes
// storage synchronously, and other tabs write the same key.
export const readStoredValue = <T,>(keyName: string): T | null => {
  try {
    const value = window.localStorage.getItem(keyName)
    return value ? (JSON.parse(value) as T) : null
  } catch {
    return null
  }
}

export const useLocalStorage = <T,>(
  keyName: string,
  defaultValue: T | null = null,
  // sync: adopt another tab's write of this key live (storage events
  // fire only in the tabs that did not write). Opt-in: right for a
  // device-wide preference, wrong as a default — the 'user' session key
  // must not swap a tab's account mid-flight because another tab logged
  // in (PR #215 review).
  options?: { sync?: boolean },
): [T | null, (value: T | null) => void] => {
  const [storedValue, setStoredValue] = useState<T | null>(() => {
    try {
      const value = window.localStorage.getItem(keyName)
      if (value) {
        return JSON.parse(value)
      } else {
        window.localStorage.setItem(keyName, JSON.stringify(defaultValue))
        return defaultValue
      }
    } catch {
      return defaultValue
    }
  })

  // the ref keeps the listener's fallback current without putting
  // defaultValue in the effect deps, where an object-literal default
  // would re-subscribe every render; written post-commit, read only in
  // event time
  const sync = options?.sync === true
  const defaultRef = useRef(defaultValue)
  useEffect(() => {
    defaultRef.current = defaultValue
  })

  // another tab wrote this key: adopt its value. The event never fires
  // in the tab that wrote, so this cannot loop with setValue below.
  useEffect(() => {
    if (!sync) return
    const onStorage = (e: StorageEvent) => {
      if (e.key !== keyName) return
      try {
        setStoredValue(e.newValue ? JSON.parse(e.newValue) : defaultRef.current)
      } catch {
        setStoredValue(defaultRef.current)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [keyName, sync])

  const setValue = useCallback(
    (newValue: T | null) => {
      try {
        window.localStorage.setItem(keyName, JSON.stringify(newValue))
      } catch (err) {
        console.log(err)
      }
      setStoredValue(newValue)
    },
    [keyName],
  )

  return [storedValue, setValue]
}
