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
// Reviewed by: Leong Wei Zhi (via pull request).

import { act, render, screen, waitFor, within } from '@testing-library/react'
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
      return jsonResponse(200, { count: unreadCountOnServer })
    }
    if (url.includes('/notifications/me/read-all')) {
      return { ok: true, status: 204, headers: new Headers() }
    }
    if (/\/notifications\/me\/\d+$/.test(url)) {
      return { ok: true, status: 204, headers: new Headers() }
    }
    if (url.includes('/notifications/me')) {
      if (listFailure) {
        return jsonResponse(listFailure.status, {
          status: listFailure.status,
          detail: listFailure.detail,
        })
      }
      const params = new URL(url).searchParams
      const page = Number(params.get('page') ?? '0')
      const source = params.get('unread') === 'true' ? unreadPages : pages
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
        [notification(12, 'request.created', { requestId: '12' }, false)],
        0,
        2,
        2,
      ),
      pageOf(
        [notification(11, 'request.created', { requestId: '11' }, true)],
        1,
        2,
        2,
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
    act(() => {
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
})
