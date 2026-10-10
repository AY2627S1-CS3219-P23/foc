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
// 2026-10-10, Claude Code (Opus 5), PR #149 re-review (@Sinnez1): the
// hook answers `complete` (a code exists is not a code is typed — see
// EditAccountCard), a refused resend keeps an expiry already known
// instead of blanking it, and the expiry countdown's last tick lands on
// the expiry rather than up to 15s past it.
// 2026-10-10, Claude Code (Opus 5), issue #167 (from the PR #149 review,
// @Sinnez1): useGateCode, the one owner of the account's gate code.
// user-service keeps a single account_update_otps row per account, but
// the two profile cards each kept their own copy of it, so a code one
// spent or replaced left the other promising a code that was gone and
// spending attempts on one that had been replaced. The hook holds the
// row and the digits typed for it, and the request both cards used to
// implement separately; ProfileSection runs it once and hands it to
// both, which keep only their own step and messages. It lives here with
// the other pieces the code steps share, as the reviewer suggested.
// Author review: Leong Wei Zhi (via PR #149).

import { useEffect, useState } from 'react'

import { profileApi } from './profileApi'
import {
  type GateFailure,
  gateFailure,
  resendWait,
  retryAfter,
} from './problemTypes'

export const CODE_LENGTH = 6

export const emptyCode = () => Array<string>(CODE_LENGTH).fill('')

// An API duration (seconds from now) as the absolute time a step counts
// down to — spelled out in each of the three of them before.
export const deadline = (seconds: number) => Date.now() + seconds * 1000

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
      // the expiry's own tick is 15s, but never past the expiry itself:
      // overshooting it left callers treating a dead code as live for up
      // to 15s (PR #149 re-review, @Sinnez1)
      cooldown > 0 ? 1000 : Math.min(15000, (expiresIn ?? 15) * 1000),
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

// What POST /users/me/otp answered: a code went out, or one was already
// in the inbox (the 429 inside the cooldown — the OTHER card may have
// asked for it), or the call failed and the caller owns the message.
export type GateRequest =
  | { kind: 'sent' }
  | { kind: 'exists'; wait: number }
  | { kind: 'failed'; error: unknown }

/**
 * The account's gate code (F2.1.1), owned once for the whole page.
 *
 * user-service keeps ONE account_update_otps row per account and both
 * profile cards draw on it: whoever requests it, either may spend it,
 * and spending or replacing it changes what the other may do. So the row
 * and the digits typed for it live here rather than in a card — two
 * copies disagreed (issue #167). Each card keeps what is genuinely its
 * own: whether it is showing the code step, and its error and notice.
 */
export interface GateCode {
  // epoch ms; null when a code exists whose remaining life was never
  // quoted (a cooldown refusal says one exists, not how long it has)
  expiresAt: number | null
  resendAt: number // epoch ms
  // seconds left on the code, null when unquoted
  life: number | null
  // seconds left of a wait the server quoted (a 429's Retry-After)
  blocked: number
  // the code may still be presented: a row is held, and its own clock
  // has not run out
  live: boolean
  digits: string[]
  // every box filled, so there is a code to submit. The code outlives
  // the card that asked for it now, so "a code exists" and "a code is
  // typed" are different questions — a card that conflates them submits
  // a blank otp (PR #149 re-review, @Sinnez1).
  complete: boolean
  setDigits: (next: string[]) => void
  // POST /users/me/otp. One call for both the first request and the
  // resend: user-service treats a repeat as the resend, which is why
  // neither the caller nor this takes a flag for it.
  request: () => Promise<GateRequest>
  // What a failed gated call means, with its effect on the shared code
  // already applied; the caller moves its own step or fields by the
  // answer (problemTypes.ts documents the four).
  failed: (error: unknown) => GateFailure
  // a gated call went through: the row is consumed server-side
  spent: () => void
}

export function useGateCode(): GateCode {
  const [gate, setGate] = useState<{
    expiresAt: number | null
    resendAt: number
  } | null>(null)
  const [digits, setDigits] = useState<string[]>(emptyCode)
  // set when the server quoted a wait: nothing may be submitted until it
  // passes, and both cards are told the same thing
  const [blockedUntil, setBlockedUntil] = useState(0)
  const { cooldown: blocked, expiresIn: life } = useOtpCountdown(
    gate?.expiresAt ?? null,
    blockedUntil,
  )

  // The in-flight flags stay with the card that asked — they label its
  // own button — so this answers rather than tracking them.
  const request = async (): Promise<GateRequest> => {
    try {
      const timings = await profileApi.requestOtp()
      setGate({
        expiresAt: deadline(timings.expiresInSeconds),
        resendAt: deadline(timings.resendInSeconds),
      })
      setDigits(emptyCode())
      return { kind: 'sent' }
    } catch (error: unknown) {
      const wait = resendWait(error)
      if (wait !== null) {
        // Not a dead end: a live code is already in the inbox, and the
        // 429 says how long before another may be asked for. A refused
        // resend never replaced the code, so anything already typed
        // still matches it and stays — and an expiry we already knew is
        // kept, since this refusal says nothing about it; only a first
        // request that lands on an existing code leaves it unquoted,
        // and then no expiry line is shown (PR #149 re-review,
        // @Sinnez1).
        setGate((previous) => ({
          expiresAt: previous?.expiresAt ?? null,
          resendAt: deadline(wait),
        }))
        return { kind: 'exists', wait }
      }
      return { kind: 'failed', error }
    }
  }

  const failed = (error: unknown): GateFailure => {
    const failure = gateFailure(error)
    const wait = retryAfter(error)
    switch (failure) {
      case 'restart':
        // no usable code any more: the row is gone or spent server-side
        setGate(null)
        setDigits(emptyCode())
        if (wait !== null) setBlockedUntil(deadline(wait))
        break
      case 'wait':
        // the code survived the refusal; only the clock is in the way
        if (wait !== null) setBlockedUntil(deadline(wait))
        break
      case 'retry':
        // the code in the inbox is still good; this guess was not
        setDigits(emptyCode())
        break
      case 'amend':
        // the values were refused without the code being read: the row
        // and the digits both stand
        break
    }
    return failure
  }

  const spent = () => {
    setGate(null)
    setDigits(emptyCode())
  }

  return {
    expiresAt: gate?.expiresAt ?? null,
    resendAt: gate?.resendAt ?? 0,
    life,
    blocked,
    live: gate !== null && life !== 0,
    digits,
    complete: digits.every((digit) => digit !== ''),
    setDigits,
    request,
    failed,
    spent,
  }
}
