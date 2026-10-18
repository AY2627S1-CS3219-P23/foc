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
    if (FakeAudioContext.resumeRejects) return Promise.reject(new Error('no'))
    if (FakeAudioContext.resumesTo) this.state = FakeAudioContext.resumesTo
    return Promise.resolve()
  })

  suspend = vi.fn(() => {
    this.state = 'suspended'
    return Promise.resolve()
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
