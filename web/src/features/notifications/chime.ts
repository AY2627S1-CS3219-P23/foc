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

// To be called from inside a user gesture (the provider hooks the first
// pointerdown): iOS Safari only unlocks a context that is created or
// resumed within a gesture handler — a later resume() from a push
// effect stays suspended forever. Harmless anywhere else.
export const unlockChime = (): void => {
  try {
    const c = liveContext()
    if (!c) return
    if (c.state === 'suspended') {
      idled = false
      void c.resume().catch(() => {})
    }
  } catch {
    // silent by requirement
  }
}

// Fire-and-forget: plays a rising two-tone chime, or does nothing at
// all when audio is unavailable (no Web Audio support), blocked by the
// autoplay policy (context stuck suspended), or muted by the tab/OS —
// the OS mute case needs no handling, the graph just plays into
// silence.
export const playChime = (): void => {
  void (async () => {
    try {
      const c = liveContext()
      if (!c) return
      clearTimeout(idleTimer)
      if (c.state === 'suspended') {
        if (idled) {
          // our own post-chime suspension: the context is already
          // unlocked, so this resume settles promptly
          await c.resume()
          idled = false
        } else {
          // Autoplay block. resume()'s promise stays PENDING until a
          // user gesture, so awaiting it here would stack every blocked
          // chime and release them together on the first click — kick
          // it off and stay silent instead (PR #215 review).
          void c.resume().catch(() => {})
          return
        }
      }
      if (c.state !== 'running') return
      const t = c.currentTime
      tone(t, 880, 0.18)
      tone(t + 0.12, 1318.5, 0.3)
      // let the audio thread idle between chimes; the flag keeps this
      // self-suspension distinguishable from a policy block above
      idleTimer = setTimeout(() => {
        idled = true
        void ctx?.suspend().catch(() => {})
      }, IDLE_AFTER_MS)
    } catch {
      // silent by requirement: blocked/suspended/muted audio is not an
      // error
    }
  })().catch(() => {})
}
