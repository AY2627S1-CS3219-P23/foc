// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
// Scope: tests for the Notification Center page against the real route
// table and a URL-dispatching fetch stub (house style — no MSW): list
// rendering per the wireframe (titles from eventMessage, unread dot vs
// grayed read rows, relative times), the empty state, click-to-mark-read
// with the badge re-fetch, mark-all-read, the All/Unread filter, the
// "Load more" pager, a pushed STOMP frame prepending live, the
// problem+json error banner, and the logged-out redirect. Interaction
// shapes are the author's options-Q&A decisions (see
// features/notifications/NotificationCenter.tsx).
// 2026-10-18: request.dropped template cases (with and without the
// enriched courierName); pending pull request review.
// Reviewed by: Leong Wei Zhi (via pull request).

import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'
import type { StompConfig, messageCallbackType } from '@stomp/stompjs'

import type {
  NotificationDto,
  PageResponse,
} from '@/features/notifications/types'
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

// the provider always builds its STOMP client; a recording stand-in
// keeps it inert and lets the prepend test drive a push
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

const minutesAgo = (minutes: number) =>
  new Date(Date.now() - minutes * 60_000).toISOString()

function notification(
  id: number,
  eventType: string,
  payload: Record<string, unknown>,
  read: boolean,
  occurredAt = minutesAgo(10),
): NotificationDto {
  return { id, eventType, payload, occurredAt, read, createdAt: occurredAt }
}

// per-test server state the dispatching fetch stub answers from
let pages: Array<PageResponse<NotificationDto>> = []
let unreadPages: Array<PageResponse<NotificationDto>> = []
let unreadCountOnServer = 0
let listFailure: { status: number; detail: string } | null = null
// fail just the list requests this answers true for, so a test can fail
// one page (or one filter) and then let the retry through
let failList: ((page: number, unread: boolean) => boolean) | null = null
let countFailure = false
// When set, unread queries page over this list and the write endpoints
// mutate it — so "mark read, then Load more" sees the set shrink the
// way the real server's does. Canned `unreadPages` still serves the
// tests that never write.
let serverUnread: NotificationDto[] | null = null
// one-shot holds, for racing a response against a push: the stub takes
// its snapshot when the request arrives and answers when the test says
let listGate: Deferred | null = null
let countGate: Deferred | null = null
let readAllGate: Deferred | null = null

interface Deferred {
  promise: Promise<void>
  release: () => void
}

function deferred(): Deferred {
  let release: () => void = () => {}
  const promise = new Promise<void>((resolve) => {
    release = resolve
  })
  return { promise, release }
}

// take a one-shot gate, so only the next request waits on it
async function passGate(gate: Deferred | null) {
  if (gate) await gate.promise
}

function pageOf(
  content: NotificationDto[],
  page: number,
  totalPages: number,
  totalElements: number,
): PageResponse<NotificationDto> {
  return { content, page, size: 10, totalElements, totalPages }
}

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
  pages = [pageOf([], 0, 0, 0)]
  unreadPages = [pageOf([], 0, 0, 0)]
  unreadCountOnServer = 0
  listFailure = null
  failList = null
  countFailure = false
  serverUnread = null
  listGate = null
  countGate = null
  readAllGate = null
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (rawUrl: string, init?: RequestInit) => {
    const url = String(rawUrl)
    if (url.includes('/users/me')) {
      return jsonResponse(200, {
        id: 1,
        email: 'e1234567@u.nus.edu',
        username: 'student_alex',
        role: 'USER',
        createdAt: '2026-09-01T00:00:00Z',
      })
    }
    if (url.includes('/notifications/me/unread-count')) {
      // the count the server would have reported when the request
      // arrived — a held answer is therefore a stale snapshot, which is
      // exactly the race the lost-update guard has to survive
      const snapshot = unreadCountOnServer
      const gate = countGate
      countGate = null
      await passGate(gate)
      if (countFailure) {
        return jsonResponse(500, { status: 500, detail: 'count is down' })
      }
      return jsonResponse(200, { count: snapshot })
    }
    if (url.includes('/notifications/me/read-all')) {
      const gate = readAllGate
      readAllGate = null
      await passGate(gate)
      if (serverUnread) serverUnread = []
      return { ok: true, status: 204, headers: new Headers() }
    }
    const patched = /\/notifications\/me\/(\d+)$/.exec(url)
    if (patched) {
      if (serverUnread) {
        const id = Number(patched[1])
        serverUnread = serverUnread.filter((n) => n.id !== id)
      }
      return { ok: true, status: 204, headers: new Headers() }
    }
    if (url.includes('/notifications/me')) {
      const params = new URL(url).searchParams
      const page = Number(params.get('page') ?? '0')
      const unread = params.get('unread') === 'true'
      const gate = listGate
      listGate = null
      await passGate(gate)
      if (listFailure) {
        return jsonResponse(listFailure.status, {
          status: listFailure.status,
          detail: listFailure.detail,
        })
      }
      if (failList?.(page, unread)) {
        return jsonResponse(500, {
          status: 500,
          detail: `page ${page} is unavailable`,
        })
      }
      if (unread && serverUnread) {
        const start = page * 10
        return jsonResponse(
          200,
          pageOf(
            serverUnread.slice(start, start + 10),
            page,
            Math.ceil(serverUnread.length / 10),
            serverUnread.length,
          ),
        )
      }
      const source = unread ? unreadPages : pages
      return jsonResponse(200, source[page] ?? pageOf([], page, 0, 0))
    }
    if (init?.method === undefined) {
      // an unmatched GET is a test-setup bug; fail loudly
      return jsonResponse(404, null)
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

async function renderCenter(loggedIn = true) {
  if (loggedIn) {
    localStorage.setItem('user', JSON.stringify(session))
  }
  render(
    <RouterProvider
      router={createMemoryRouter(routes, {
        initialEntries: ['/notifications'],
      })}
    />,
  )
  // settle the initial fetches (list, unread count, /users/me)
  await act(async () => {})
}

// requests the list endpoint saw, as parsed query params
function listRequests() {
  return fetchMock.mock.calls
    .map(([url]) => String(url))
    .filter((url) => /\/notifications\/me(\?|$)/.test(url))
    .map((url) => new URL(url).searchParams)
}

// the provider's STOMP client, connected, with its message callback
function connectPush() {
  act(() => stomp.instances[0].config.onConnect?.(undefined as never))
  return stomp.instances[0].subscribe.mock.calls[0][1] as messageCallbackType
}

const pushFrame = (dto: NotificationDto) =>
  ({ body: JSON.stringify(dto) }) as Parameters<messageCallbackType>[0]

// "Request #1048 accepted by courier_maya"
const acceptedBy = (id: number) =>
  notification(
    id,
    'request.accepted',
    { requestId: '1048', courierName: 'courier_maya' },
    false,
  )

const listRows = () => within(screen.getByRole('list')).getAllByRole('button')

describe('notification center page', () => {
  test("renders the caller's rows: titles, unread dot rows clickable, read rows inert and grayed", async () => {
    pages = [
      pageOf(
        [
          notification(
            3,
            'request.accepted',
            { requestId: '1048', courierName: 'courier_maya' },
            false,
          ),
          notification(
            2,
            'request.courier-arrived',
            { requestId: '1042' },
            false,
            minutesAgo(25),
          ),
          notification(
            1,
            'request.completed',
            { requestId: '1039' },
            true,
            minutesAgo(3 * 60),
          ),
        ],
        0,
        1,
        3,
      ),
    ]
    unreadCountOnServer = 2
    await renderCenter()

    // display-name copy (backend D29) and the catalog templates
    const unreadRow = screen.getByRole('button', {
      name: 'Request #1048 accepted by courier_maya — unread, select to mark read',
    })
    expect(unreadRow).toBeEnabled()
    expect(unreadRow).toHaveTextContent('10 min ago')
    screen.getByRole('button', {
      name: 'Courier has arrived at drop-off for Request #1042 — unread, select to mark read',
    })
    // a read row is inert — no mark-unread control exists on purpose
    const readRow = screen.getByRole('button', {
      name: 'Request #1039 marked as completed',
    })
    expect(readRow).toBeDisabled()
    expect(readRow).toHaveTextContent('3h ago')
  })

  test('a dropped request names the courier, and degrades without a name', async () => {
    pages = [
      pageOf(
        [
          notification(
            2,
            'request.dropped',
            { requestId: '1051', courierName: 'courier_maya' },
            false,
          ),
          // enrichment can fail (D30): the copy degrades, never breaks
          notification(1, 'request.dropped', { requestId: '1052' }, false),
        ],
        0,
        1,
        2,
      ),
    ]
    unreadCountOnServer = 2
    await renderCenter()

    screen.getByRole('button', {
      name: 'Request #1051 dropped by courier_maya — unread, select to mark read',
    })
    screen.getByRole('button', {
      name: 'Request #1052 was dropped by its courier — unread, select to mark read',
    })
  })

  test("shows the wireframe's empty state when there is nothing", async () => {
    await renderCenter()

    screen.getByText('No new notifications')
    screen.getByText("You're all caught up for today.")
  })

  test('clicking an unread row PATCHes it read and re-fetches the badge count', async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        [notification(7, 'request.cancelled', { requestId: '1045' }, false)],
        0,
        1,
        1,
      ),
    ]
    unreadCountOnServer = 1
    await renderCenter()
    await screen.findByRole('link', { name: 'Notifications (1 unread)' })

    // the write succeeds, after which the server would report 0 unread
    unreadCountOnServer = 0
    await user.click(
      screen.getByRole('button', {
        name: 'Request #1045 cancelled — unread, select to mark read',
      }),
    )

    const patch = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith('/notifications/me/7'),
    )
    expect(patch?.[1]).toMatchObject({ method: 'PATCH' })
    expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ read: true })
    // row grayed in place, badge re-synced from the server (not locally
    // decremented — options Q&A decision)
    expect(
      screen.getByRole('button', { name: 'Request #1045 cancelled' }),
    ).toBeDisabled()
    await screen.findByRole('link', { name: 'Notifications' })
  })

  test('"Mark all as read" hits the bulk endpoint, grays every row and zeroes the badge', async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        [
          notification(2, 'request.expired', { requestId: '1050' }, false),
          notification(1, 'request.created', { requestId: '1049' }, false),
        ],
        0,
        1,
        2,
      ),
    ]
    unreadCountOnServer = 2
    await renderCenter()
    await screen.findByRole('link', { name: 'Notifications (2 unread)' })

    unreadCountOnServer = 0
    await user.click(screen.getByRole('button', { name: 'Mark all as read' }))

    expect(
      fetchMock.mock.calls.some(
        ([url, init]) =>
          String(url).endsWith('/notifications/me/read-all') &&
          (init as RequestInit)?.method === 'POST',
      ),
    ).toBe(true)
    expect(
      screen.getByRole('button', { name: 'Request #1050 expired' }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'Request #1049 created' }),
    ).toBeDisabled()
    await screen.findByRole('link', { name: 'Notifications' })
  })

  test('the "Mark all as read" control is disabled at zero unread', async () => {
    await renderCenter()

    expect(
      screen.getByRole('button', { name: 'Mark all as read' }),
    ).toBeDisabled()
  })

  test('the Unread filter re-queries with unread=true', async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        [
          notification(2, 'request.created', { requestId: '2' }, false),
          notification(1, 'request.created', { requestId: '1' }, true),
        ],
        0,
        1,
        2,
      ),
    ]
    unreadPages = [
      pageOf(
        [notification(2, 'request.created', { requestId: '2' }, false)],
        0,
        1,
        1,
      ),
    ]
    await renderCenter()

    await user.click(screen.getByRole('button', { name: 'Unread' }))

    await waitFor(() =>
      expect(listRequests().some((p) => p.get('unread') === 'true')).toBe(true),
    )
    const list = screen.getByRole('list')
    expect(within(list).getAllByRole('button')).toHaveLength(1)
    within(list).getByRole('button', {
      name: 'Request #2 created — unread, select to mark read',
    })
  })

  test('"Load more" appends the next page under the first', async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        [
          notification(12, 'request.created', { requestId: '12' }, false),
          ...Array.from({ length: 9 }, (_, i) =>
            notification(
              20 + i,
              'request.created',
              { requestId: `2${i}` },
              true,
            ),
          ),
        ],
        0,
        2,
        11,
      ),
      pageOf(
        [notification(11, 'request.created', { requestId: '11' }, true)],
        1,
        2,
        11,
      ),
    ]
    await renderCenter()
    screen.getByRole('button', {
      name: 'Request #12 created — unread, select to mark read',
    })

    await user.click(screen.getByRole('button', { name: 'Load more' }))

    expect(listRequests().some((p) => p.get('page') === '1')).toBe(true)
    // both pages visible, oldest appended last, pager gone (no page 2)
    screen.getByRole('button', { name: 'Request #11 created' })
    screen.getByRole('button', {
      name: 'Request #12 created — unread, select to mark read',
    })
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
  })

  test('a notification pushed while the page is open prepends live', async () => {
    pages = [
      pageOf(
        [notification(1, 'request.created', { requestId: '1' }, true)],
        0,
        1,
        1,
      ),
    ]
    await renderCenter()
    expect(stomp.instances).toHaveLength(1)

    // drive the provider's STOMP client: connect, then a pushed frame
    act(() => stomp.instances[0].config.onConnect?.(undefined as never))
    const onMessage = stomp.instances[0].subscribe.mock
      .calls[0][1] as messageCallbackType
    await act(async () => {
      onMessage({
        body: JSON.stringify(
          notification(
            99,
            'request.accepted',
            { requestId: '1048', courierName: 'courier_maya' },
            false,
          ),
        ),
      } as Parameters<messageCallbackType>[0])
    })

    const list = screen.getByRole('list')
    const rows = within(list).getAllByRole('button')
    expect(rows[0]).toHaveAccessibleName(
      'Request #1048 accepted by courier_maya — unread, select to mark read',
    )
    expect(rows).toHaveLength(2)
  })

  test('a failed list load shows the problem+json detail in the error banner', async () => {
    listFailure = { status: 500, detail: 'notifications are on fire' }
    await renderCenter()

    screen.getByText('notifications are on fire')
  })

  test('logged out, /notifications bounces to the login page', async () => {
    await renderCenter(false)

    screen.getByRole('heading', { name: 'Log In' })
    expect(screen.queryByText('No new notifications')).toBeNull()
  })

  // ---- PR #199 review: one case per finding ----

  test('a failed "Load more" retries its own page instead of skipping it', async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        Array.from({ length: 10 }, (_, i) =>
          notification(30 + i, 'request.created', { requestId: `3${i}` }, true),
        ),
        0,
        2,
        11,
      ),
      pageOf(
        [notification(11, 'request.created', { requestId: '11' }, true)],
        1,
        2,
        11,
      ),
    ]
    failList = (page) => page === 1
    await renderCenter()

    await user.click(screen.getByRole('button', { name: 'Load more' }))

    // the page failed, so the pager stays and still points at that page
    await screen.findByText('page 1 is unavailable')
    expect(screen.getByRole('button', { name: 'Load more' })).toBeEnabled()

    failList = null
    await user.click(screen.getByRole('button', { name: 'Load more' }))

    const requested = listRequests().map((p) => p.get('page'))
    // asked for page 1 twice and never skipped ahead to page 2
    expect(requested.filter((p) => p === '1')).toHaveLength(2)
    expect(requested).not.toContain('2')
    screen.getByRole('button', { name: 'Request #11 created' })
  })

  test("switching filters drops the other filter's page count", async () => {
    const user = userEvent.setup()
    pages = [
      pageOf(
        [notification(2, 'request.created', { requestId: '2' }, false)],
        0,
        3,
        3,
      ),
    ]
    // every unread request fails, so nothing replaces the page count
    failList = (_page, unread) => unread
    await renderCenter()
    expect(screen.getByRole('button', { name: 'Load more' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: 'Unread' }))

    await screen.findByText('page 0 is unavailable')
    // "All" had 3 pages; offering page 1 here would skip the Unread
    // view's own first page for good
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
  })

  test('a frame pushed while the first page loads survives its answer', async () => {
    pages = [
      pageOf(
        [notification(1, 'request.created', { requestId: '1' }, true)],
        0,
        1,
        1,
      ),
    ]
    // hold page 0: its snapshot predates the push that follows
    listGate = deferred()
    const held = listGate
    localStorage.setItem('user', JSON.stringify(session))
    render(
      <RouterProvider
        router={createMemoryRouter(routes, {
          initialEntries: ['/notifications'],
        })}
      />,
    )
    await act(async () => {})

    const onMessage = connectPush()
    await act(async () => onMessage(pushFrame(acceptedBy(99))))

    await act(async () => {
      held.release()
    })

    const rows = listRows()
    expect(rows).toHaveLength(2)
    // newest on top, and not replaced away by the stale page
    expect(rows[0]).toHaveAccessibleName(
      'Request #1048 accepted by courier_maya — unread, select to mark read',
    )
  })

  test('under the Unread filter, a row marked read leaves the list', async () => {
    const user = userEvent.setup()
    unreadPages = [
      pageOf(
        [
          notification(2, 'request.created', { requestId: '2' }, false),
          notification(1, 'request.expired', { requestId: '1' }, false),
        ],
        0,
        1,
        2,
      ),
    ]
    unreadCountOnServer = 2
    await renderCenter()
    await user.click(screen.getByRole('button', { name: 'Unread' }))
    await waitFor(() => expect(listRows()).toHaveLength(2))

    unreadCountOnServer = 1
    await user.click(
      screen.getByRole('button', {
        name: 'Request #2 created — unread, select to mark read',
      }),
    )

    // an Unread view must not keep showing a row it just marked read
    expect(listRows()).toHaveLength(1)
    expect(
      screen.queryByRole('button', { name: 'Request #2 created' }),
    ).toBeNull()
    screen.getByRole('button', {
      name: 'Request #1 expired — unread, select to mark read',
    })
  })

  test('"Mark all as read" empties the Unread view', async () => {
    const user = userEvent.setup()
    serverUnread = [
      notification(2, 'request.created', { requestId: '2' }, false),
      notification(1, 'request.expired', { requestId: '1' }, false),
    ]
    unreadCountOnServer = 2
    await renderCenter()
    await user.click(screen.getByRole('button', { name: 'Unread' }))
    await waitFor(() => expect(listRows()).toHaveLength(2))

    unreadCountOnServer = 0
    await user.click(screen.getByRole('button', { name: 'Mark all as read' }))

    await screen.findByText('No new notifications')
    expect(screen.queryByRole('list')).toBeNull()
  })

  test('"Mark all as read" leaves no pager under the Unread empty state', async () => {
    const user = userEvent.setup()
    // 13 unread over 2 pages, so the page count outlives the rows if
    // nothing clears it (found by driving the real app)
    serverUnread = Array.from({ length: 13 }, (_, i) =>
      notification(100 + i, 'request.created', { requestId: `${i}` }, false),
    )
    unreadCountOnServer = 13
    await renderCenter()
    await user.click(screen.getByRole('button', { name: 'Unread' }))
    await waitFor(() => expect(listRows()).toHaveLength(10))
    expect(screen.getByRole('button', { name: 'Load more' })).toBeEnabled()

    unreadCountOnServer = 0
    await user.click(screen.getByRole('button', { name: 'Mark all as read' }))

    await screen.findByText('No new notifications')
    // an empty list with a "Load more" under it offers more of nothing
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
  })

  test('"Load more" in the Unread view skips no row as the set shrinks', async () => {
    const user = userEvent.setup()
    // 13 unread; page 0 is the first 10. Marking 3 read shortens the
    // server's set to 10, so a page-1 request (offset 10) would return
    // nothing and rows 11-13 would never be reachable (PR #199 review).
    serverUnread = Array.from({ length: 13 }, (_, i) =>
      notification(200 + i, 'request.created', { requestId: `${i}` }, false),
    )
    unreadCountOnServer = 13
    await renderCenter()
    await user.click(screen.getByRole('button', { name: 'Unread' }))
    await waitFor(() => expect(listRows()).toHaveLength(10))

    for (const i of [0, 1, 2]) {
      await user.click(
        screen.getByRole('button', {
          name: `Request #${i} created — unread, select to mark read`,
        }),
      )
    }
    expect(listRows()).toHaveLength(7)

    await user.click(screen.getByRole('button', { name: 'Load more' }))

    // every unread row the server still has is now on screen, none lost
    await waitFor(() => expect(listRows()).toHaveLength(10))
    for (const i of [10, 11, 12]) {
      screen.getByRole('button', {
        name: `Request #${i} created — unread, select to mark read`,
      })
    }
    // and nothing is left to ask for
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
  })

  test('a pushed row does not retire the pager while older rows wait', async () => {
    const user = userEvent.setup()
    // 15 on the server, page 0 holds 10. Five pushes bring the rows
    // held to 15 as well — against a total those rows postdate, that
    // reads as "everything is loaded" and the five oldest become
    // unreachable until a reload (PR #199 review).
    pages = [
      pageOf(
        Array.from({ length: 10 }, (_, i) =>
          notification(300 + i, 'request.created', { requestId: `${i}` }, true),
        ),
        0,
        2,
        15,
      ),
      pageOf(
        Array.from({ length: 5 }, (_, i) =>
          notification(
            310 + i,
            'request.created',
            { requestId: `${10 + i}` },
            true,
          ),
        ),
        1,
        2,
        15,
      ),
    ]
    await renderCenter()
    const onMessage = connectPush()
    await act(async () => {
      for (let i = 0; i < 5; i += 1) onMessage(pushFrame(acceptedBy(900 + i)))
    })
    expect(listRows()).toHaveLength(15)

    // the pager is still there, and still has the older page behind it
    await user.click(screen.getByRole('button', { name: 'Load more' }))

    await waitFor(() => expect(listRows()).toHaveLength(20))
    screen.getByRole('button', { name: 'Request #14 created' })
  })

  test('a row read in the Unread view does not conjure a pager', async () => {
    const user = userEvent.setup()
    // exactly one page of unread rows: reading one takes it out of the
    // server's set too, so there is still nothing left to ask for
    serverUnread = Array.from({ length: 10 }, (_, i) =>
      notification(400 + i, 'request.created', { requestId: `${i}` }, false),
    )
    unreadCountOnServer = 10
    await renderCenter()
    await user.click(screen.getByRole('button', { name: 'Unread' }))
    await waitFor(() => expect(listRows()).toHaveLength(10))
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()

    await user.click(
      screen.getByRole('button', {
        name: 'Request #0 created — unread, select to mark read',
      }),
    )

    await waitFor(() => expect(listRows()).toHaveLength(9))
    // 9 rows held against a total of 9, not the 10 the last answer saw
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
  })

  test('"Mark all as read" leaves a frame pushed after the bulk update unread', async () => {
    pages = [
      pageOf(
        [notification(1, 'request.created', { requestId: '1' }, false)],
        0,
        1,
        1,
      ),
    ]
    unreadCountOnServer = 1
    await renderCenter()
    const onMessage = connectPush()

    // hold the POST open: the server has already marked everything read
    readAllGate = deferred()
    const held = readAllGate
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Mark all as read' }))
    })
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith('/notifications/me/read-all'),
      ),
    ).toBe(true)

    // this row is stored after that update, so it is still unread there
    await act(async () => onMessage(pushFrame(acceptedBy(99))))
    await act(async () => {
      held.release()
    })

    expect(
      screen.getByRole('button', { name: 'Request #1 created' }),
    ).toBeDisabled()
    // marking it read here would leave the badge counting a row the
    // list renders as inert, with no way to click it
    expect(
      screen.getByRole('button', {
        name: 'Request #1048 accepted by courier_maya — unread, select to mark read',
      }),
    ).toBeEnabled()
  })

  test('a failed load shows no "all caught up" empty state', async () => {
    listFailure = { status: 500, detail: 'notifications are on fire' }
    await renderCenter()

    screen.getByText('notifications are on fire')
    expect(screen.queryByText('No new notifications')).toBeNull()
    expect(screen.queryByText("You're all caught up for today.")).toBeNull()
  })

  test('"Mark all as read" survives a failed badge count', async () => {
    // the list loads; only the best-effort count endpoint is down
    countFailure = true
    pages = [
      pageOf(
        [notification(1, 'request.created', { requestId: '1' }, false)],
        0,
        1,
        1,
      ),
    ]
    await renderCenter()

    screen.getByRole('button', {
      name: 'Request #1 created — unread, select to mark read',
    })
    // an unread row on screen is proof there is something to mark
    expect(
      screen.getByRole('button', { name: 'Mark all as read' }),
    ).toBeEnabled()
  })

  test('two frames delivered in one render both prepend', async () => {
    pages = [
      pageOf(
        [notification(1, 'request.created', { requestId: '1' }, true)],
        0,
        1,
        1,
      ),
    ]
    await renderCenter()
    const onMessage = connectPush()

    // one WebSocket message carrying two frames: both callbacks run in
    // the same task, so React commits them together
    await act(async () => {
      onMessage(
        pushFrame(
          notification(98, 'request.expired', { requestId: '98' }, false),
        ),
      )
      onMessage(
        pushFrame(
          notification(99, 'request.created', { requestId: '99' }, false),
        ),
      )
    })

    const rows = listRows()
    expect(rows).toHaveLength(3)
    // newest first, and neither frame lost to the other
    expect(rows[0]).toHaveAccessibleName(
      'Request #99 created — unread, select to mark read',
    )
    expect(rows[1]).toHaveAccessibleName(
      'Request #98 expired — unread, select to mark read',
    )
  })

  test('a stale count answer cannot erase a pushed increment', async () => {
    unreadCountOnServer = 0
    await renderCenter()

    // the connect-time sync snapshots 0 and is held open
    countGate = deferred()
    const held = countGate
    const onMessage = connectPush()

    // the server stores and pushes a notification after that snapshot
    unreadCountOnServer = 1
    await act(async () => {
      onMessage(pushFrame(acceptedBy(99)))
    })
    await screen.findByRole('link', { name: 'Notifications (1 unread)' })

    // the stale answer lands last and must not be believed
    await act(async () => {
      held.release()
    })

    screen.getByRole('link', { name: 'Notifications (1 unread)' })
  })

  test('relative times tick while the page sits open', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      pages = [
        pageOf(
          [
            notification(
              1,
              'request.created',
              { requestId: '1' },
              false,
              new Date().toISOString(),
            ),
          ],
          0,
          1,
          1,
        ),
      ]
      await renderCenter()
      expect(listRows()[0]).toHaveTextContent('just now')

      await act(async () => {
        vi.advanceTimersByTime(2 * 60_000)
      })

      expect(listRows()[0]).toHaveTextContent('2 min ago')
    } finally {
      vi.useRealTimers()
    }
  })
})
