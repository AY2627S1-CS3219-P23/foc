// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: tests for the app's single STOMP client and the bell badge
// against a mocked @stomp/stompjs Client — connect config (broker URL
// through the gateway, bearer CONNECT header, exponential 1 s → 30 s
// backoff), no client while logged out or without a base URL, badge
// counting pushed frames, teardown on logout and on a STOMP ERROR.
// PR #185 Copilot review: the badge count joins the bell's accessible
// name, so the queries here follow it.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1): the
// ERROR-frame case rewritten for the bounded retry and the stopped
// state the bell now reports.
// 2026-10-17, Claude Code (Fable 5), issue #66: the bell is a link to
// the Notification Center now, so its queries ask for a link; the
// blanket fetch mock became URL-dispatching (the provider now also
// calls GET /notifications/me/unread-count, which must not be answered
// with the /users/me body); a seed test covers the REST-seeded badge
// composing with pushed frames.
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-18, Claude Code (Fable 5), notification sound (follow-up to
// issue #66, no issue yet): chime cases — once per live frame, once for
// a burst batched into one render, never for REST seeds/reconnects or
// the logout reset, and the localStorage toggle (default on) gates it.
// The chime module itself is mocked; its audio has its own unit test.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@Sinnez1): cases
// for the review fixes — a frame that fails to parse still chimes (it
// still bumps the badge), a hidden tab stays quiet, another tab's mute
// lands via the storage event without a reload.

import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'
import type { StompConfig, messageCallbackType } from '@stomp/stompjs'

import { serviceBaseUrls } from '@/lib/api/config'
import { brokerUrl } from '@/features/notifications/stompUrl'
import { routes } from '../routes'

vi.mock('@/lib/api/config', () => ({
  serviceBaseUrls: {
    user: 'http://user.test',
    supplier: '',
    order: '',
    credit: '',
    notification: 'http://gateway.test/api/notification',
  },
}))

// a recording stand-in for @stomp/stompjs's Client: tests drive
// onConnect / onStompError and the captured subscribe callback
const stomp = vi.hoisted(() => {
  const instances: Array<{
    config: StompConfig
    activate: ReturnType<typeof vi.fn>
    deactivate: ReturnType<typeof vi.fn>
    subscribe: ReturnType<typeof vi.fn>
  }> = []
  return { instances }
})

// the chime is a side effect the provider must trigger at the right
// moments; whether it can make sound at all is chime.test.ts's problem
const chime = vi.hoisted(() => ({ playChime: vi.fn(), unlockChime: vi.fn() }))
vi.mock('@/features/notifications/chime', () => chime)

vi.mock('@stomp/stompjs', () => {
  class Client {
    config: StompConfig
    activate = vi.fn()
    deactivate = vi.fn().mockResolvedValue(undefined)
    subscribe = vi.fn()

    constructor(config: StompConfig) {
      this.config = config
      stomp.instances.push(this)
    }
  }
  return {
    Client,
    ReconnectionTimeMode: { LINEAR: 0, EXPONENTIAL: 1 },
  }
})

const fetchMock = vi.fn()
// what GET /notifications/me/unread-count answers; per-test override
let unreadCountOnServer = 0

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status < 400,
    status,
    headers: new Headers(),
    json: async () => body,
  }
}

beforeEach(() => {
  localStorage.clear()
  stomp.instances.length = 0
  unreadCountOnServer = 0
  chime.playChime.mockClear()
  chime.unlockChime.mockClear()
  fetchMock.mockReset()
  // dispatch on the URL: AuthProvider asks GET /users/me, the
  // notification provider asks GET /notifications/me/unread-count —
  // a blanket body would feed the badge the /users/me object
  fetchMock.mockImplementation(async (url: string) => {
    if (String(url).includes('/notifications/me/unread-count')) {
      return jsonResponse(200, { count: unreadCountOnServer })
    }
    if (String(url).includes('/users/me')) {
      return jsonResponse(200, {
        id: 1,
        email: 'e1234567@u.nus.edu',
        username: 'student_alex',
        role: 'USER',
        createdAt: '2026-09-01T00:00:00Z',
      })
    }
    return jsonResponse(404, null)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

const session = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 }

function renderApp(loggedIn: boolean) {
  if (loggedIn) {
    localStorage.setItem('user', JSON.stringify(session))
  }
  render(
    <RouterProvider
      router={createMemoryRouter(routes, { initialEntries: ['/'] })}
    />,
  )
}

// the one client the provider created this test
function client() {
  expect(stomp.instances).toHaveLength(1)
  return stomp.instances[0]
}

// drive the server side: connection established, then pushed frames
function connectAndSubscribe() {
  act(() => client().config.onConnect?.(undefined as never))
  expect(client().subscribe).toHaveBeenCalledWith(
    '/user/queue/notifications',
    expect.any(Function),
  )
  return client().subscribe.mock.calls[0][1] as messageCallbackType
}

function pushFrame(onMessage: messageCallbackType) {
  act(() => {
    onMessage({
      body: JSON.stringify({
        id: 1,
        eventType: 'request.accepted',
        payload: { requestId: 'ord-1' },
        occurredAt: '2026-10-12T08:30:00Z',
        read: false,
        createdAt: '2026-10-12T08:30:01Z',
      }),
    } as Parameters<messageCallbackType>[0])
  })
}

describe('stomp broker URL', () => {
  test('swaps http for ws and appends the raw-WebSocket path', () => {
    expect(brokerUrl('http://localhost:8090/api/notification')).toBe(
      'ws://localhost:8090/api/notification/ws/websocket',
    )
  })

  test('swaps https for wss and drops a trailing slash', () => {
    expect(brokerUrl('https://foc.example/api/notification/')).toBe(
      'wss://foc.example/api/notification/ws/websocket',
    )
  })
})

describe('notification STOMP client', () => {
  test('no client while logged out', () => {
    renderApp(false)

    expect(stomp.instances).toHaveLength(0)
  })

  test('connects through the gateway with the session token and the documented reconnect policy', () => {
    renderApp(true)

    const { config, activate } = client()
    expect(config.brokerURL).toBe(
      'ws://gateway.test/api/notification/ws/websocket',
    )
    expect(config.connectHeaders).toEqual({ Authorization: 'Bearer jwt' })
    // "Session mechanics": 1 s doubling to a 30 s cap, 10 s heartbeats
    expect(config.reconnectDelay).toBe(1_000)
    expect(config.maxReconnectDelay).toBe(30_000)
    expect(config.reconnectTimeMode).toBe(1) // EXPONENTIAL
    expect(config.heartbeatIncoming).toBe(10_000)
    expect(config.heartbeatOutgoing).toBe(10_000)
    expect(activate).toHaveBeenCalled()
  })

  test('warns and skips the socket when the notification URL is unset', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    // the mocked config module is mutable; restore for the other tests
    const base = serviceBaseUrls.notification
    ;(serviceBaseUrls as { notification: string }).notification = ''

    try {
      renderApp(true)

      expect(stomp.instances).toHaveLength(0)
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('VITE_NOTIFICATION_SERVICE_URL'),
      )
    } finally {
      ;(serviceBaseUrls as { notification: string }).notification = base
    }
  })

  test('pushed frames raise the bell badge count and its accessible name', () => {
    renderApp(true)

    const onMessage = connectAndSubscribe()
    const bell = screen.getByRole('link', { name: 'Notifications' })
    expect(bell).not.toHaveTextContent('1')

    pushFrame(onMessage)
    pushFrame(onMessage)

    expect(bell).toHaveTextContent('2')
    // aria-label overrides the badge text, so the count must be in the
    // accessible name for screen readers (PR #185 Copilot review)
    expect(screen.getByRole('link', { name: 'Notifications (2 unread)' })).toBe(
      bell,
    )
  })

  test('the badge seeds from the REST unread count and pushes stack on top (issue #66)', async () => {
    unreadCountOnServer = 5
    renderApp(true)

    // the seed is a fetch; wait for it to land on the badge
    const bell = await screen.findByRole('link', {
      name: 'Notifications (5 unread)',
    })
    expect(bell).toHaveTextContent('5')

    const onMessage = connectAndSubscribe()
    // let the resync-on-connect land first (it answers 5 again) so the
    // pushed frame below increments the settled baseline
    await act(async () => {})
    pushFrame(onMessage)
    expect(
      screen.getByRole('link', { name: 'Notifications (6 unread)' }),
    ).toHaveTextContent('6')
  })

  test('resubscribes on every (re)connect', () => {
    renderApp(true)

    connectAndSubscribe()
    act(() => client().config.onConnect?.(undefined as never))

    expect(client().subscribe).toHaveBeenCalledTimes(2)
  })

  test('logout tears the socket down and clears the badge', async () => {
    const user = userEvent.setup()
    renderApp(true)
    const onMessage = connectAndSubscribe()
    pushFrame(onMessage)
    expect(
      screen.getByRole('link', { name: 'Notifications (1 unread)' }),
    ).toHaveTextContent('1')

    await user.click(await screen.findByRole('button', { name: 'Logout' }))

    await waitFor(() => expect(client().deactivate).toHaveBeenCalled())
    expect(
      screen.getByRole('link', { name: 'Notifications' }),
    ).not.toHaveTextContent('1')
  })

  test('an ERROR frame stops the client and retries it once the delay passes', () => {
    vi.useFakeTimers()
    try {
      renderApp(true)
      expect(client().activate).toHaveBeenCalledTimes(1)

      act(() => client().config.onStompError?.(undefined as never))

      // the client's own 1 s reconnect must not re-send the rejected
      // CONNECT; the provider re-activates on its own schedule instead
      expect(client().deactivate).toHaveBeenCalled()
      expect(client().activate).toHaveBeenCalledTimes(1)

      act(() => vi.advanceTimersByTime(30_000))

      expect(client().activate).toHaveBeenCalledTimes(2)
      // still live as far as the bell is concerned
      screen.getByRole('link', { name: 'Notifications' })
    } finally {
      vi.useRealTimers()
    }
  })

  test('push stops and the bell says so after the retry budget runs out', () => {
    vi.useFakeTimers()
    try {
      renderApp(true)
      const onMessage = connectAndSubscribe()
      pushFrame(onMessage)

      // three retries, then the fourth ERROR gives up
      for (let i = 0; i < 3; i++) {
        act(() => client().config.onStompError?.(undefined as never))
        act(() => vi.advanceTimersByTime(30_000))
      }
      expect(client().activate).toHaveBeenCalledTimes(4)

      act(() => client().config.onStompError?.(undefined as never))
      act(() => vi.advanceTimersByTime(30_000))

      // no fifth attempt, and the count is kept but no longer sold as live
      expect(client().activate).toHaveBeenCalledTimes(4)
      const bell = screen.getByRole('link', {
        name: 'Notifications (1 unread) — updates paused, sign in again',
      })
      expect(bell).toHaveTextContent('1')
    } finally {
      vi.useRealTimers()
    }
  })

  test('a successful reconnect earns a fresh retry budget', () => {
    vi.useFakeTimers()
    try {
      renderApp(true)

      for (let i = 0; i < 3; i++) {
        act(() => client().config.onStompError?.(undefined as never))
        act(() => vi.advanceTimersByTime(30_000))
      }
      // the last retry reached CONNECTED, so the next ERROR is a new
      // failure rather than the end of the old one
      act(() => client().config.onConnect?.(undefined as never))

      act(() => client().config.onStompError?.(undefined as never))
      act(() => vi.advanceTimersByTime(30_000))

      expect(client().activate).toHaveBeenCalledTimes(5)
      screen.getByRole('link', { name: 'Notifications' })
    } finally {
      vi.useRealTimers()
    }
  })

  test('a live frame chimes once; a burst batched into one render chimes once', () => {
    renderApp(true)
    const onMessage = connectAndSubscribe()

    pushFrame(onMessage)
    expect(chime.playChime).toHaveBeenCalledTimes(1)

    // two frames delivered in a single WebSocket message run their
    // callbacks in one task — one committed render, one chime
    act(() => {
      for (const id of [2, 3]) {
        onMessage({
          body: JSON.stringify({
            id,
            eventType: 'request.accepted',
            payload: { requestId: `ord-${id}` },
            occurredAt: '2026-10-12T08:30:00Z',
            read: false,
            createdAt: '2026-10-12T08:30:01Z',
          }),
        } as Parameters<messageCallbackType>[0])
      }
    })
    expect(chime.playChime).toHaveBeenCalledTimes(2)
  })

  test('no chime for the REST seed, the reconnect catch-up, or the logout reset', async () => {
    const user = userEvent.setup()
    unreadCountOnServer = 5
    renderApp(true)
    // the login seed lands on the badge without a sound
    await screen.findByRole('link', { name: 'Notifications (5 unread)' })

    const onMessage = connectAndSubscribe()
    await act(async () => {}) // connect re-sync settles
    // a reconnect catch-up can jump the count; it must stay silent too
    act(() => client().config.onConnect?.(undefined as never))
    await act(async () => {})
    expect(chime.playChime).not.toHaveBeenCalled()

    pushFrame(onMessage)
    expect(chime.playChime).toHaveBeenCalledTimes(1)

    // logout empties the pushed queue — a reset, not an arrival
    await user.click(await screen.findByRole('button', { name: 'Logout' }))
    expect(chime.playChime).toHaveBeenCalledTimes(1)
  })

  test('the stored toggle gates the chime and defaults to on', () => {
    localStorage.setItem('notificationSoundEnabled', 'false')
    renderApp(true)
    pushFrame(connectAndSubscribe())
    expect(chime.playChime).not.toHaveBeenCalled()
  })

  test('an unset toggle means on, and the default is written back', () => {
    renderApp(true)
    // the hook makes the default explicit on first read
    expect(localStorage.getItem('notificationSoundEnabled')).toBe('true')
    pushFrame(connectAndSubscribe())
    expect(chime.playChime).toHaveBeenCalledTimes(1)
  })

  test('a frame that fails to parse still chimes, like it still bumps the badge', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    renderApp(true)
    const onMessage = connectAndSubscribe()

    act(() => {
      onMessage({ body: 'not json' } as Parameters<messageCallbackType>[0])
    })

    expect(
      screen.getByRole('link', { name: 'Notifications (1 unread)' }),
    ).toBeInTheDocument()
    expect(chime.playChime).toHaveBeenCalledTimes(1)
  })

  test('a hidden tab stays quiet — every open tab receives the push', () => {
    const visibility = vi
      .spyOn(document, 'visibilityState', 'get')
      .mockReturnValue('hidden')
    try {
      renderApp(true)
      pushFrame(connectAndSubscribe())
      expect(chime.playChime).not.toHaveBeenCalled()
    } finally {
      visibility.mockRestore()
    }
  })

  test("another tab's mute lands via the storage event, no reload needed", () => {
    renderApp(true)
    const onMessage = connectAndSubscribe()

    // a storage event only ever fires in the tabs that did NOT write
    act(() => {
      localStorage.setItem('notificationSoundEnabled', 'false')
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: 'notificationSoundEnabled',
          newValue: 'false',
        }),
      )
    })

    pushFrame(onMessage)
    expect(chime.playChime).not.toHaveBeenCalled()
  })

  test('the first pointerdown hands the gesture to the audio unlock', async () => {
    const user = userEvent.setup()
    renderApp(true)

    await user.click(screen.getByRole('link', { name: 'Notifications' }))

    expect(chime.unlockChime).toHaveBeenCalled()
  })

  test('the bell reports a paused channel when the notification URL is unset', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    const base = serviceBaseUrls.notification
    ;(serviceBaseUrls as { notification: string }).notification = ''

    try {
      renderApp(true)

      // no socket will ever open, so the bell must not imply a live one
      screen.getByRole('link', {
        name: 'Notifications — updates paused, sign in again',
      })
    } finally {
      ;(serviceBaseUrls as { notification: string }).notification = base
    }
  })
})
