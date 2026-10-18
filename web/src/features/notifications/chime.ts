// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-18, notification sound (follow-up
// to issue #66, no issue yet). Scope: a short synthesized bell chime
// (author decision via options Q&A: Web Audio synthesis, no binary
// asset) played by NotificationProvider on live pushes. Every failure
// path is deliberately silent — blocked or muted audio is a user/agent
// choice, not an error.
// Author review: Leong Wei Zhi to review via the PR.

// One context for the page's lifetime: browsers cap concurrent
// AudioContexts (~6), so per-chime construction would eventually fail.
let ctx: AudioContext | null = null

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

// Fire-and-forget: plays a rising two-tone chime, or does nothing at
// all when audio is unavailable (no Web Audio support), blocked by the
// autoplay policy (context stuck suspended), or muted by the tab/OS —
// the OS mute case needs no handling, the graph just plays into
// silence.
export const playChime = (): void => {
  void (async () => {
    try {
      const Ctor =
        window.AudioContext ??
        (window as { webkitAudioContext?: typeof AudioContext })
          .webkitAudioContext
      if (!Ctor) return
      ctx ??= new Ctor()
      if (ctx.state === 'suspended') await ctx.resume()
      // still not running (autoplay policy before any user gesture):
      // return before scheduling — tones queued against a suspended
      // context would all burst out on a later gesture
      if (ctx.state !== 'running') return
      const t = ctx.currentTime
      tone(t, 880, 0.18)
      tone(t + 0.12, 1318.5, 0.3)
    } catch {
      // silent by requirement
    }
  })().catch(() => {})
}
