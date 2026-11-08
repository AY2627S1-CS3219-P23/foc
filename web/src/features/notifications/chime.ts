// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-18, notification sound (follow-up
// to issue #66, no issue yet). Scope: a short synthesized bell chime
// (author decision via options Q&A: Web Audio synthesis, no binary
// asset) played by NotificationProvider on live pushes. Every failure
// path is deliberately silent — blocked or muted audio is a user/agent
// choice, not an error.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@Sinnez1): a
// policy-blocked resume() is no longer awaited — the promise stays
// pending until a user gesture, so stacked awaits all released on the
// first click and chimed together; unlockChime() added for iOS Safari,
// which only unlocks a context created/resumed inside a gesture; a
// closed context (device change, iOS interruption) is replaced instead
// of reused; the context suspends itself shortly after a chime so the
// audio thread can idle, with a flag so that self-suspension is woken
// by the next chime rather than mistaken for an autoplay block.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-11-09, Claude Code (Opus 5.5), notification sound preview (no
// issue): the sound toggle's preview chime (author decision via options
// Q&A: switching the sound on previews it on both surfaces).
// PR #314 review (@Sinnez1): instead of a `gesture` flag that awaited
// any resume, unlockChime keeps its in-flight resume and a chime waits
// on that real unlock, bounded (UNLOCK_WAIT_MS) so a synthetic click's
// never-settling resume holds nothing back; a pending idle suspend is
// waited out before deciding; one chime waits at a time; and an
// `enabled` check after the wait drops a preview the user switched off
// meanwhile.
// Author review: Leong Wei Zhi to review via the PR.

// One context for the page's lifetime: browsers cap concurrent
// AudioContexts (~6), so per-chime construction would eventually fail.
let ctx: AudioContext | null = null
// the context suspended by US after a chime (to idle the audio thread),
// as opposed to by the autoplay policy: our own suspension may simply be
// resumed and played through; a policy block must not be waited on
let idled = false
let idleTimer: ReturnType<typeof setTimeout> | undefined

// the chime's tail ends ~0.47 s in; idle a beat after that
const IDLE_AFTER_MS = 600

const contextCtor = () =>
  window.AudioContext ??
  (window as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext

// the reusable context, replacing one that reached closed (an audio
// device change, or an iOS interruption, kills it for good — resume()
// on it only throws)
const liveContext = (): AudioContext | null => {
  const Ctor = contextCtor()
  if (!Ctor) return null
  if (!ctx || ctx.state === 'closed') {
    ctx = new Ctor()
    idled = false
  }
  return ctx
}

// one bell partial: a sine through a gain with a fast attack and an
// exponential decay (exponential ramps cannot reach 0, hence the floor)
const tone = (at: number, freq: number, dur: number) => {
  if (!ctx) return
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = 'sine'
  osc.frequency.value = freq
  gain.gain.setValueAtTime(0.0001, at)
  gain.gain.exponentialRampToValueAtTime(0.08, at + 0.02)
  gain.gain.exponentialRampToValueAtTime(0.0001, at + dur)
  osc.connect(gain).connect(ctx.destination)
  osc.start(at)
  osc.stop(at + dur + 0.05)
}

// unlockChime's resume(), while it is in flight. Started inside a user
// gesture it settles promptly, so a chime may wait for it — bounded,
// since a synthetic click (a script's element.click(), assistive tech)
// is no user activation and its resume stays pending (PR #314 review).
let unlocking: Promise<void> | null = null
// the idle timer's suspend(), while in flight: the context still
// reports running until it lands, so a chime scheduled meanwhile would
// be cut off by it (PR #314 review)
let suspending: Promise<void> | null = null
// one chime waits at a time; a call made while it waits joins it rather
// than stacking a second, overlapping chime (PR #314 review)
let waiting = false

// how long a chime waits for an in-flight unlock before it gives up and
// stays silent — long enough for iOS Safari's slow resume
const UNLOCK_WAIT_MS = 1_000

const settled = (p: Promise<void>): Promise<void> =>
  p.then(
    () => {},
    () => {},
  )

// p, or giving up after ms — whichever comes first
const within = (p: Promise<void>, ms: number): Promise<void> => {
  let timer: ReturnType<typeof setTimeout> | undefined
  return Promise.race([
    p,
    new Promise<void>((resolve) => {
      timer = setTimeout(resolve, ms)
    }),
  ]).finally(() => clearTimeout(timer))
}

// To be called from inside a user gesture (the provider hooks the first
// pointerdown, and the sound toggle calls it from its click): iOS Safari
// only unlocks a context that is created or resumed within a gesture
// handler — a later resume() from a push effect stays suspended
// forever. Harmless anywhere else.
export const unlockChime = (): void => {
  try {
    const c = liveContext()
    if (!c) return
    if (c.state === 'suspended') {
      idled = false
      const p = settled(c.resume())
      unlocking = p
      void p.then(() => {
        if (unlocking === p) unlocking = null
      })
    }
  } catch {
    // silent by requirement
  }
}

// Fire-and-forget: plays a rising two-tone chime, or does nothing at
// all when audio is unavailable (no Web Audio support), blocked by the
// autoplay policy (context stuck suspended), or muted by the tab/OS —
// the OS mute case needs no handling, the graph just plays into
// silence. `enabled` is asked again after any wait: the sound may have
// been switched off while this call waited (PR #314 review).
export const playChime = ({
  enabled = () => true,
}: { enabled?: () => boolean } = {}): void => {
  if (waiting) return
  void (async () => {
    try {
      const c = liveContext()
      if (!c) return
      clearTimeout(idleTimer)
      waiting = true
      try {
        // our own idle suspend still landing: let it, then wake below
        if (suspending) await suspending
        if (c.state === 'suspended') {
          if (idled) {
            // our own post-chime suspension: the context is already
            // unlocked, so this resume settles promptly
            await c.resume()
            idled = false
          } else if (unlocking) {
            // a gesture's unlock in flight (the toggle's own click, or a
            // push landing just after the first pointerdown): wait for
            // it, but never past the bound — a resume that will not
            // settle must not hold a chime back for a later gesture
            await within(unlocking, UNLOCK_WAIT_MS)
          } else {
            // Autoplay block. resume()'s promise stays PENDING until a
            // user gesture, so awaiting it here would stack every
            // blocked chime and release them together on the first
            // click — kick it off and stay silent instead (PR #215
            // review).
            void c.resume().catch(() => {})
            return
          }
        }
      } finally {
        waiting = false
      }
      if (!enabled() || c.state !== 'running') return
      const t = c.currentTime
      tone(t, 880, 0.18)
      tone(t + 0.12, 1318.5, 0.3)
      // let the audio thread idle between chimes; the flag keeps this
      // self-suspension distinguishable from a policy block above
      idleTimer = setTimeout(() => {
        idled = true
        const p = settled(c.suspend())
        suspending = p
        void p.then(() => {
          if (suspending === p) suspending = null
        })
      }, IDLE_AFTER_MS)
    } catch {
      // silent by requirement: blocked/suspended/muted audio is not an
      // error
    }
  })().catch(() => {})
}
