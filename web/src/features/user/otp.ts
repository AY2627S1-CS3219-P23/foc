// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-07, issue #112 (PR #149).
// Scope: the non-component pieces every emailed-code step shares — the
// code's length, and the two countdowns it runs (the resend cooldown and
// the code's remaining life) — lifted out of
// OtpVerificationModal (PR #150) now that the profile page's three code
// steps need the same thing. Behaviour is that file's, unchanged,
// including the reasons its review settled: times are absolute and owned
// by the caller, so a step closed and reopened later shows what is really
// left rather than restarting at 60s, and the timer keeps running while
// EITHER line is still moving (keyed on the cooldown alone, the expiry
// sentence froze the moment the cooldown ran out).
// Reviewed by: [pending]

import { useEffect, useState } from 'react'

export const CODE_LENGTH = 6

export const emptyCode = () => Array<string>(CODE_LENGTH).fill('')

// Whole seconds left on each line. expiresAt null means "unknown" — the
// cooldown-429 path, where a live code exists but its remaining TTL was
// never quoted; the caller omits the expiry line rather than guessing.
export function useOtpCountdown(
  expiresAt: number | null,
  resendAt: number,
): { cooldown: number; expiresIn: number | null } {
  const [now, setNow] = useState(() => Date.now())
  const [times, setTimes] = useState({ expiresAt, resendAt })

  // a resend (or a refusal quoting a wait) moves the times under us: read
  // the clock at once rather than letting the pending timeout decide when
  // the new countdown starts, which could be up to 15s away. Adjusted
  // during render — React's own answer for state that depends on changed
  // props — because an effect would render the stale countdown first.
  if (times.expiresAt !== expiresAt || times.resendAt !== resendAt) {
    setTimes({ expiresAt, resendAt })
    // updater form: the clock is read when React applies the update, not
    // during this render, which has to stay pure
    setNow(() => Date.now())
  }

  const cooldown = Math.max(0, Math.ceil((resendAt - now) / 1000))
  const expiresIn =
    expiresAt === null ? null : Math.max(0, Math.ceil((expiresAt - now) / 1000))

  // One pending timeout at a time, re-armed by its own state change — the
  // pattern used elsewhere in web/ (features/user/components/UsersSection)
  // and cleaned up for free, which matters because these steps unmount on
  // close, on success, and twice under StrictMode. The cooldown needs
  // per-second precision; the expiry is quoted in minutes, so on its own
  // it doesn't deserve a ticking second hand.
  useEffect(() => {
    if (cooldown <= 0 && !expiresIn) return
    const timer = setTimeout(
      () => setNow(Date.now()),
      cooldown > 0 ? 1000 : 15000,
    )
    return () => clearTimeout(timer)
  }, [cooldown, expiresIn])

  return { cooldown, expiresIn }
}

// The expiry is quoted in minutes rather than counted down to the second —
// no wireframe draws a timer — but it keeps up with the clock.
export function describeExpiry(seconds: number): string {
  if (seconds >= 60) {
    const minutes = Math.round(seconds / 60)
    return minutes === 1 ? '1 minute' : `${minutes} minutes`
  }
  return seconds === 1 ? '1 second' : `${seconds} seconds`
}
