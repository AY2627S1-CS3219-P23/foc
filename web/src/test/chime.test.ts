// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-18, notification sound
// (follow-up to issue #66, no issue yet). Scope: unit tests for the
// synthesized chime against a fake AudioContext (jsdom has no Web
// Audio): scheduling and the context singleton, and every silent
// failure path — suspended context that resumes, one that stays
// suspended (autoplay policy), a rejecting resume, a missing
// constructor, a throwing one. Each test re-imports the module for a
// fresh singleton.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@Sinnez1): the
// fake's resume() can now stay pending forever, like a policy-blocked
// one does, and the cases follow the fixes — blocked calls never stack
// and burst on the unlock, a closed context is replaced, the context
// idles after a chime and wakes for the next, unlockChime resumes
// inside a gesture.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-11-09, Claude Code (Opus 5.5), notification sound preview (no
// issue), PR #314 review (@Sinnez1): the fake's resume() and suspend()
// can land after a delay, and the cases cover a chime waiting on an
// in-flight unlock — one that lands, one that never settles (bounded,
// nothing fires late), one that rejects — a switch-off while it waits,
// a second call joining the wait, and a pending idle suspend waited out.
// Re-review: an idle suspend or idle resume that never settles is given
// up on after the bound, and the next chime still plays.

class FakeGainParam {
  setValueAtTime = vi.fn()
  exponentialRampToValueAtTime = vi.fn()
}

class FakeNode {
  connect = vi.fn((node: unknown) => node)
}

class FakeOscillator extends FakeNode {
  type = ''
  frequency = { value: 0 }
  start = vi.fn()
  stop = vi.fn()
}

class FakeGain extends FakeNode {
  gain = new FakeGainParam()
}

class FakeAudioContext {
  static instances: FakeAudioContext[] = []
  static initialState = 'running'
  static resumesTo: string | null = null
  static resumeRejects = false
  // a policy-blocked resume(): the promise never settles until a user
  // gesture — which a test simulates by setting `state` directly
  static resumeNeverSettles = false
  // a resume()/suspend() that lands after this many ms, not at once —
  // iOS Safari's slow resume, a suspend still in flight
  static resumeAfterMs: number | null = null
  static suspendAfterMs: number | null = null
  // a resume()/suspend() that never settles at all, while the context
  // still reports its old state — a hung audio thread (PR #314
  // re-review)
  static idleResumeNeverSettles = false
  static suspendNeverSettles = false

  state = FakeAudioContext.initialState
  currentTime = 1
  destination = new FakeNode()
  oscillators: FakeOscillator[] = []
  gains: FakeGain[] = []

  constructor() {
    FakeAudioContext.instances.push(this)
  }

  resume = vi.fn(() => {
    if (FakeAudioContext.resumeNeverSettles) return new Promise<void>(() => {})
    if (FakeAudioContext.idleResumeNeverSettles && this.state === 'suspended')
      return new Promise<void>(() => {})
    if (FakeAudioContext.resumeRejects) return Promise.reject(new Error('no'))
    const land = () => {
      if (FakeAudioContext.resumesTo) this.state = FakeAudioContext.resumesTo
    }
    const after = FakeAudioContext.resumeAfterMs
    if (after === null) {
      land()
      return Promise.resolve()
    }
    return new Promise<void>((resolve) =>
      setTimeout(() => {
        land()
        resolve()
      }, after),
    )
  })

  suspend = vi.fn(() => {
    if (FakeAudioContext.suspendNeverSettles) return new Promise<void>(() => {})
    const after = FakeAudioContext.suspendAfterMs
    if (after === null) {
      this.state = 'suspended'
      return Promise.resolve()
    }
    return new Promise<void>((resolve) =>
      setTimeout(() => {
        this.state = 'suspended'
        resolve()
      }, after),
    )
  })

  createOscillator() {
    const osc = new FakeOscillator()
    this.oscillators.push(osc)
    return osc
  }

  createGain() {
    const gain = new FakeGain()
    this.gains.push(gain)
    return gain
  }
}

// the module keeps its context in module state; a fresh import per test
// is the singleton reset
async function importChime() {
  vi.resetModules()
  return await import('@/features/notifications/chime')
}

// playChime is fire-and-forget; its async body needs the microtask
// queue drained before asserting
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

beforeEach(() => {
  FakeAudioContext.instances.length = 0
  FakeAudioContext.initialState = 'running'
  FakeAudioContext.resumesTo = null
  FakeAudioContext.resumeRejects = false
  FakeAudioContext.resumeNeverSettles = false
  FakeAudioContext.resumeAfterMs = null
  FakeAudioContext.suspendAfterMs = null
  FakeAudioContext.idleResumeNeverSettles = false
  FakeAudioContext.suspendNeverSettles = false
  vi.stubGlobal('AudioContext', FakeAudioContext)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('playChime', () => {
  test('schedules a two-tone chime with a decay envelope on one reused context', async () => {
    const { playChime } = await importChime()

    playChime()
    await flush()

    expect(FakeAudioContext.instances).toHaveLength(1)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.oscillators).toHaveLength(2)
    const [low, high] = ctx.oscillators
    expect(low.frequency.value).toBe(880)
    expect(high.frequency.value).toBe(1318.5)
    for (const osc of ctx.oscillators) {
      expect(osc.type).toBe('sine')
      expect(osc.start).toHaveBeenCalled()
      expect(osc.stop).toHaveBeenCalled()
    }
    // attack then decay, both exponential ramps, per tone
    for (const gain of ctx.gains) {
      expect(gain.gain.setValueAtTime).toHaveBeenCalledWith(
        0.0001,
        expect.any(Number),
      )
      expect(gain.gain.exponentialRampToValueAtTime).toHaveBeenCalledTimes(2)
    }
    // the second tone starts after the first — a rising chime
    expect(high.start.mock.calls[0][0]).toBeGreaterThan(
      low.start.mock.calls[0][0] as number,
    )

    playChime()
    await flush()
    // same context reused: browsers cap how many a page may hold
    expect(FakeAudioContext.instances).toHaveLength(1)
    expect(ctx.oscillators).toHaveLength(4)
  })

  test('calls under an autoplay block never stack and burst out on the unlock', async () => {
    FakeAudioContext.initialState = 'suspended'
    FakeAudioContext.resumeNeverSettles = true
    const { playChime } = await importChime()

    // three pushes land before any user gesture
    playChime()
    playChime()
    playChime()
    await flush()

    const ctx = FakeAudioContext.instances[0]
    expect(ctx.resume).toHaveBeenCalled()
    expect(ctx.oscillators).toHaveLength(0)

    // the user clicks: the browser lets the context start — nothing
    // held back may fire late (PR #215 review: an awaited resume() left
    // three chimes pending here, all released together)
    ctx.state = 'running'
    await flush()
    expect(ctx.oscillators).toHaveLength(0)

    // the next real arrival chimes normally
    playChime()
    await flush()
    expect(ctx.oscillators).toHaveLength(2)
  })

  test('stays silent when the context will not leave suspended (autoplay policy)', async () => {
    FakeAudioContext.initialState = 'suspended'
    const { playChime } = await importChime()

    playChime()
    await flush()

    // nothing scheduled: queued tones would burst out on a later gesture
    expect(FakeAudioContext.instances[0].oscillators).toHaveLength(0)
  })

  test('swallows a rejecting resume with no unhandled rejection', async () => {
    FakeAudioContext.initialState = 'suspended'
    FakeAudioContext.resumeRejects = true
    const { playChime } = await importChime()

    playChime()
    await flush()

    expect(FakeAudioContext.instances[0].oscillators).toHaveLength(0)
  })

  test('idles the context after a chime and wakes it for the next', async () => {
    vi.useFakeTimers()
    try {
      const { playChime } = await importChime()

      playChime()
      await vi.advanceTimersByTimeAsync(0)
      const ctx = FakeAudioContext.instances[0]
      expect(ctx.oscillators).toHaveLength(2)

      // the chime has rung out; the audio thread gets to idle
      await vi.advanceTimersByTimeAsync(1_000)
      expect(ctx.suspend).toHaveBeenCalled()
      expect(ctx.state).toBe('suspended')

      // our own suspension is not an autoplay block: the next chime
      // resumes and plays through
      FakeAudioContext.resumesTo = 'running'
      playChime()
      await vi.advanceTimersByTimeAsync(0)
      expect(ctx.resume).toHaveBeenCalled()
      expect(ctx.oscillators).toHaveLength(4)
    } finally {
      vi.useRealTimers()
    }
  })

  test('replaces a context that reached closed instead of reusing it', async () => {
    const { playChime } = await importChime()

    playChime()
    await flush()
    expect(FakeAudioContext.instances).toHaveLength(1)

    // an audio device change or iOS interruption killed it for good
    FakeAudioContext.instances[0].state = 'closed'
    playChime()
    await flush()

    expect(FakeAudioContext.instances).toHaveLength(2)
    expect(FakeAudioContext.instances[1].oscillators).toHaveLength(2)
  })

  test('does nothing without Web Audio support', async () => {
    vi.unstubAllGlobals() // plain jsdom: no AudioContext at all
    const { playChime } = await importChime()

    expect(() => playChime()).not.toThrow()
    await flush()
  })

  test('swallows a throwing constructor', async () => {
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          throw new Error('denied')
        }
      },
    )
    const { playChime } = await importChime()

    expect(() => playChime()).not.toThrow()
    await flush()
  })
})

// a chime waiting on unlockChime's in-flight resume (the sound toggle's
// own click, or a push just after the first pointerdown) — PR #314
// review: bounded, re-checked, never stacked
describe('playChime after an unlock', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeAudioContext.initialState = 'suspended'
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('waits for an in-flight unlock and plays once it lands', async () => {
    FakeAudioContext.resumesTo = 'running'
    FakeAudioContext.resumeAfterMs = 300
    const { unlockChime, playChime } = await importChime()

    unlockChime()
    playChime()
    await vi.advanceTimersByTimeAsync(0)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.oscillators).toHaveLength(0)

    await vi.advanceTimersByTimeAsync(300)
    expect(ctx.oscillators).toHaveLength(2)
  })

  test('gives up on an unlock that never settles, and nothing fires late', async () => {
    // a synthetic click: no user activation, so the resume stays pending
    FakeAudioContext.resumeNeverSettles = true
    const { unlockChime, playChime } = await importChime()

    unlockChime()
    playChime()
    await vi.advanceTimersByTimeAsync(1_000)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.oscillators).toHaveLength(0)

    // a real gesture later starts the context: the given-up chime must
    // not ring out then
    ctx.state = 'running'
    await vi.advanceTimersByTimeAsync(1_000)
    expect(ctx.oscillators).toHaveLength(0)
  })

  test('stays silent on an unlock that rejects', async () => {
    FakeAudioContext.resumeRejects = true
    const { unlockChime, playChime } = await importChime()

    unlockChime()
    playChime()
    await vi.advanceTimersByTimeAsync(0)

    expect(FakeAudioContext.instances[0].oscillators).toHaveLength(0)
  })

  test('drops a chime whose sound was switched off while it waited', async () => {
    FakeAudioContext.resumesTo = 'running'
    FakeAudioContext.resumeAfterMs = 300
    const { unlockChime, playChime } = await importChime()
    let on = true

    unlockChime()
    playChime({ enabled: () => on })
    await vi.advanceTimersByTimeAsync(100)
    on = false
    await vi.advanceTimersByTimeAsync(200)

    expect(FakeAudioContext.instances[0].oscillators).toHaveLength(0)
  })

  test('a call made while one waits joins it instead of stacking', async () => {
    FakeAudioContext.resumesTo = 'running'
    FakeAudioContext.resumeAfterMs = 300
    const { unlockChime, playChime } = await importChime()

    unlockChime()
    playChime()
    playChime()
    await vi.advanceTimersByTimeAsync(300)

    expect(FakeAudioContext.instances[0].oscillators).toHaveLength(2)
  })

  test('waits out a pending idle suspend before deciding', async () => {
    FakeAudioContext.initialState = 'running'
    FakeAudioContext.suspendAfterMs = 100
    const { playChime } = await importChime()

    playChime()
    await vi.advanceTimersByTimeAsync(0)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.oscillators).toHaveLength(2)

    // the idle timer fires; its suspend is still in flight, so the
    // context reports running
    await vi.advanceTimersByTimeAsync(650)
    expect(ctx.suspend).toHaveBeenCalled()
    expect(ctx.state).toBe('running')

    FakeAudioContext.resumesTo = 'running'
    playChime()
    await vi.advanceTimersByTimeAsync(0)
    // nothing scheduled into a context about to suspend
    expect(ctx.oscillators).toHaveLength(2)

    // the suspend lands, the chime wakes the context and plays
    await vi.advanceTimersByTimeAsync(100)
    expect(ctx.resume).toHaveBeenCalled()
    expect(ctx.state).toBe('running')
    expect(ctx.oscillators).toHaveLength(4)
  })
})

// PR #314 re-review: a wait that never settles must not keep the
// one-at-a-time latch set, or every later chime is silenced
describe('playChime with a wait that never settles', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('gives up on an idle suspend that never lands, and the next chime plays', async () => {
    FakeAudioContext.suspendNeverSettles = true
    const { playChime } = await importChime()

    playChime()
    await vi.advanceTimersByTimeAsync(650)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.oscillators).toHaveLength(2)
    expect(ctx.suspend).toHaveBeenCalled()

    // waits on the stuck suspend for the bound, then plays into the
    // still-running context
    playChime()
    await vi.advanceTimersByTimeAsync(1_000)
    expect(ctx.oscillators).toHaveLength(4)

    // and the latch is free again
    await vi.advanceTimersByTimeAsync(650)
    playChime()
    await vi.advanceTimersByTimeAsync(1_000)
    expect(ctx.oscillators).toHaveLength(6)
  })

  test('gives up on an idle resume that never settles, and a later chime still plays', async () => {
    FakeAudioContext.idleResumeNeverSettles = true
    const { playChime } = await importChime()

    playChime()
    await vi.advanceTimersByTimeAsync(650)
    const ctx = FakeAudioContext.instances[0]
    expect(ctx.state).toBe('suspended')

    playChime()
    await vi.advanceTimersByTimeAsync(1_000)
    // the resume hung: silent, but not stuck
    expect(ctx.oscillators).toHaveLength(2)

    // the audio thread recovers; the next chime is not locked out
    FakeAudioContext.idleResumeNeverSettles = false
    FakeAudioContext.resumesTo = 'running'
    playChime()
    await vi.advanceTimersByTimeAsync(0)
    expect(ctx.oscillators).toHaveLength(4)
  })
})

describe('unlockChime', () => {
  test('creates and resumes the context inside the gesture, so the next chime plays', async () => {
    FakeAudioContext.initialState = 'suspended'
    FakeAudioContext.resumesTo = 'running'
    const { unlockChime, playChime } = await importChime()

    unlockChime()
    await flush()

    const ctx = FakeAudioContext.instances[0]
    expect(ctx.resume).toHaveBeenCalled()
    expect(ctx.state).toBe('running')
    // no sound from the unlock itself
    expect(ctx.oscillators).toHaveLength(0)

    playChime()
    await flush()
    expect(ctx.oscillators).toHaveLength(2)
  })

  test('is a silent no-op without Web Audio support', async () => {
    vi.unstubAllGlobals()
    const { unlockChime } = await importChime()

    expect(() => unlockChime()).not.toThrow()
  })
})
