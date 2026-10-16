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
// Reviewed by: Leong Wei Zhi (via pull request).

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

beforeEach(() => {
  localStorage.clear()
  stomp.instances.length = 0
  fetchMock.mockReset()
  // AuthProvider asks GET /users/me once a session exists
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers(),
    json: async () => ({
      id: 1,
      email: 'e1234567@u.nus.edu',
      username: 'student_alex',
      role: 'USER',
      createdAt: '2026-09-01T00:00:00Z',
    }),
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
    const bell = screen.getByRole('button', { name: 'Notifications' })
    expect(bell).not.toHaveTextContent('1')

    pushFrame(onMessage)
    pushFrame(onMessage)

    expect(bell).toHaveTextContent('2')
    // aria-label overrides the badge text, so the count must be in the
    // accessible name for screen readers (PR #185 Copilot review)
    expect(
      screen.getByRole('button', { name: 'Notifications (2 unread)' }),
    ).toBe(bell)
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
      screen.getByRole('button', { name: 'Notifications (1 unread)' }),
    ).toHaveTextContent('1')

    await user.click(await screen.findByRole('button', { name: 'Logout' }))

    await waitFor(() => expect(client().deactivate).toHaveBeenCalled())
    expect(
      screen.getByRole('button', { name: 'Notifications' }),
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
      screen.getByRole('button', { name: 'Notifications' })
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
      const bell = screen.getByRole('button', {
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
      screen.getByRole('button', { name: 'Notifications' })
    } finally {
      vi.useRealTimers()
    }
  })

  test('the bell reports a paused channel when the notification URL is unset', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    const base = serviceBaseUrls.notification
    ;(serviceBaseUrls as { notification: string }).notification = ''

    try {
      renderApp(true)

      // no socket will ever open, so the bell must not imply a live one
      screen.getByRole('button', {
        name: 'Notifications — updates paused, sign in again',
      })
    } finally {
      ;(serviceBaseUrls as { notification: string }).notification = base
    }
  })
})
