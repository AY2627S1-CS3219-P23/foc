// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-11-04.
// Scope: timed refetch for the order pages while a request is in
// progress. Author's decisions: every 5 s, on My Requests and Active
// Delivery, paused while the tab is hidden, with a refetch at once when
// it is shown again (CreditBalanceProvider's pattern). A safety net
// next to useRequestRefetch's push trigger, not a replacement.
// Author review: Ko-Khan, pending pull request review.

import { useEffect, useRef } from 'react'

export const ORDER_POLL_MS = 5000

// Calls onTick every ORDER_POLL_MS while `active` and the tab is
// visible. onTick is read through a ref, so callers need not memoize it.
export function usePollWhileActive(active: boolean, onTick: () => void): void {
  const onTickRef = useRef(onTick)
  useEffect(() => {
    onTickRef.current = onTick
  })

  useEffect(() => {
    if (!active) return

    let timer: ReturnType<typeof setInterval> | undefined
    const startPolling = () => {
      clearInterval(timer)
      timer = setInterval(() => onTickRef.current(), ORDER_POLL_MS)
    }

    // hidden: nothing to keep current; shown again: catch up at once,
    // then restart the interval from there
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        onTickRef.current()
        startPolling()
      } else {
        clearInterval(timer)
      }
    }

    if (document.visibilityState === 'visible') startPolling()
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [active])
}
