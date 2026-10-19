// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-19, issue #221.
// Scope: unit tests for the read-time username resolver (D32, supersedes
// D30/D31) — in-flight dedupe, the 10 min positive / 30 s negative TTLs
// (fake timers), 404 and network failures degrading to null, and the
// hook-facing cache-reset test hook. House style: URL-dispatching fetch
// stub, no MSW (see notification-center.test.tsx).
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-19, Claude Code (Fable 5), PR #222 review (@claude bot): hook
// recovery case added — a lookup that fails once succeeds through the
// mounted hook's 30 s re-resolve tick after the negative TTL lapses.

import { act, renderHook } from '@testing-library/react'

import {
  clearUsernameCache,
  resolveUsername,
  useUsernames,
} from '@/features/user/usernames'

vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://user.test',
    supplier: '',
    order: '',
    credit: '',
    notification: '',
  },
}))

const fetchMock = vi.fn()

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status < 400,
    status,
    headers: new Headers(),
    json: async () => body,
  }
}

// id → username; missing ids answer the public-profile route's 404
let usernamesOnServer: Record<string, string> = {}

beforeEach(() => {
  clearUsernameCache()
  usernamesOnServer = { 'usr-cou-2002': 'courier_maya' }
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (rawUrl: string) => {
    const match = /^http:\/\/user\.test\/users\/([^/?]+)$/.exec(String(rawUrl))
    if (!match) return jsonResponse(404, null)
    const username = usernamesOnServer[decodeURIComponent(match[1])]
    if (username === undefined) {
      return jsonResponse(404, { status: 404, detail: 'User not found' })
    }
    return jsonResponse(200, { username })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

// the lookups this module issued (ignores anything else on the stub)
const lookups = () =>
  fetchMock.mock.calls.filter(([url]) => /\/users\/[^/?]+$/.test(String(url)))
    .length

describe('resolveUsername', () => {
  test('resolves a username and caches it — a second call issues no fetch', async () => {
    await expect(resolveUsername('usr-cou-2002')).resolves.toBe('courier_maya')
    await expect(resolveUsername('usr-cou-2002')).resolves.toBe('courier_maya')
    expect(lookups()).toBe(1)
  })

  test('dedupes concurrent calls for the same id into one request', async () => {
    const [first, second] = await Promise.all([
      resolveUsername('usr-cou-2002'),
      resolveUsername('usr-cou-2002'),
    ])
    expect(first).toBe('courier_maya')
    expect(second).toBe('courier_maya')
    expect(lookups()).toBe(1)
  })

  test('an unknown id answers null and is negative-cached for 30s, not 10min', async () => {
    vi.useFakeTimers()
    await expect(resolveUsername('usr-ghost')).resolves.toBeNull()
    await expect(resolveUsername('usr-ghost')).resolves.toBeNull()
    expect(lookups()).toBe(1)

    // past the negative TTL the resolver asks again — recovery (the
    // user reappearing, an outage ending) is not muted for 10 minutes
    vi.advanceTimersByTime(31_000)
    usernamesOnServer['usr-ghost'] = 'returned_user'
    await expect(resolveUsername('usr-ghost')).resolves.toBe('returned_user')
    expect(lookups()).toBe(2)
  })

  test('a cached name expires after the positive TTL and refetches', async () => {
    vi.useFakeTimers()
    await expect(resolveUsername('usr-cou-2002')).resolves.toBe('courier_maya')

    // inside the TTL: served from cache
    vi.advanceTimersByTime(9 * 60_000)
    await expect(resolveUsername('usr-cou-2002')).resolves.toBe('courier_maya')
    expect(lookups()).toBe(1)

    // past it: a rename shows up
    vi.advanceTimersByTime(2 * 60_000)
    usernamesOnServer['usr-cou-2002'] = 'courier_renamed'
    await expect(resolveUsername('usr-cou-2002')).resolves.toBe(
      'courier_renamed',
    )
    expect(lookups()).toBe(2)
  })

  test('a network failure degrades to null, never throws', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('network down'))
    await expect(resolveUsername('usr-cou-2002')).resolves.toBeNull()
  })

  test('ids are URL-encoded on the wire', async () => {
    usernamesOnServer['usr/odd id'] = 'odd_user'
    await expect(resolveUsername('usr/odd id')).resolves.toBe('odd_user')
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://user.test/users/usr%2Fodd%20id',
    )
  })
})

describe('useUsernames', () => {
  test("a lookup that fails once recovers on the mounted hook's tick after the negative TTL (PR #222 review)", async () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useUsernames(['usr-ghost']))

    // first resolve 404s and negative-caches; the map stays empty
    await act(async () => {})
    expect(result.current.get('usr-ghost')).toBeUndefined()
    expect(lookups()).toBe(1)

    // nothing re-fetches before the first 30 s tick
    await act(async () => {
      await vi.advanceTimersByTimeAsync(29_000)
    })
    expect(lookups()).toBe(1)

    // the user becomes resolvable; the tick lands past the negative
    // TTL, re-fetches, and the name arrives without the id set changing
    usernamesOnServer['usr-ghost'] = 'returned_user'
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000)
    })
    await act(async () => {})
    expect(result.current.get('usr-ghost')).toBe('returned_user')
    expect(lookups()).toBe(2)
  })
})
