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

import { useState } from 'react'

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

  const setValue = (newValue: T | null) => {
    try {
      window.localStorage.setItem(keyName, JSON.stringify(newValue))
    } catch (err) {
      console.log(err)
    }
    setStoredValue(newValue)
  }

  return [storedValue, setValue]
}
