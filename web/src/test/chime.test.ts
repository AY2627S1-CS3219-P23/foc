// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-18, notification sound
// (follow-up to issue #66, no issue yet). Scope: unit tests for the
// synthesized chime against a fake AudioContext (jsdom has no Web
// Audio): scheduling and the context singleton, and every silent
// failure path — suspended context that resumes, one that stays
// suspended (autoplay policy), a rejecting resume, a missing
// constructor, a throwing one. Each test re-imports the module for a
// fresh singleton.
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

  state = FakeAudioContext.initialState
  currentTime = 1
  destination = new FakeNode()
  oscillators: FakeOscillator[] = []
  gains: FakeGain[] = []

  constructor() {
    FakeAudioContext.instances.push(this)
  }

  resume = vi.fn(async () => {
    if (FakeAudioContext.resumeRejects) throw new Error('blocked')
    if (FakeAudioContext.resumesTo) this.state = FakeAudioContext.resumesTo
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

  test('resumes a suspended context and plays once it is running', async () => {
    FakeAudioContext.initialState = 'suspended'
    FakeAudioContext.resumesTo = 'running'
    const { playChime } = await importChime()

    playChime()
    await flush()

    const ctx = FakeAudioContext.instances[0]
    expect(ctx.resume).toHaveBeenCalled()
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
