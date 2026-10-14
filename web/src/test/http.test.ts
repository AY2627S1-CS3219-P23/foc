// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-14, PR #182 re-review (@Sinnez1).
// Scope: unit tests for apiFetch's two contracts around the
// unauthorized handler (issue #178) — which Authorization headers count
// as a sent bearer token, and that a failing handler does not replace
// the ApiError a caller was promised. The feature-level cases live in
// auth.test.tsx; these two are easier to state against apiFetch itself.
// Author review: Leong Wei Zhi; Ryan Ang to re-review via PR #182.

import {
  ApiError,
  apiFetch,
  errorMessage,
  setTokenSource,
  setUnauthorizedHandler,
} from '@/lib/api/http'

// user-service's URL is empty in tests; give apiFetch one to call
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

function refusal() {
  return {
    ok: false,
    status: 401,
    headers: new Headers(),
    json: async () => ({ status: 401, detail: 'Token has expired' }),
  }
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(refusal())
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  // module state: leave neither behind for the next test
  setTokenSource(() => null)
  setUnauthorizedHandler(() => {})
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('apiFetch: the refused token handed to the unauthorized handler', () => {
  test('is read off the header the request carried', async () => {
    const refused = vi.fn()
    setUnauthorizedHandler(refused)
    setTokenSource(() => 'jwt')

    await expect(apiFetch('user', '/users/me')).rejects.toBeInstanceOf(ApiError)

    expect(refused).toHaveBeenCalledWith('jwt')
  })

  // PR #182 re-review (@Sinnez1): the gateway matches the scheme
  // case-insensitively, so a header it accepts — and can answer 401 —
  // has to reach the handler here too
  test('accepts a lower-case bearer scheme, as the gateway does', async () => {
    const refused = vi.fn()
    setUnauthorizedHandler(refused)

    await expect(
      apiFetch('user', '/users/me', {
        headers: { Authorization: 'bearer jwt' },
      }),
    ).rejects.toBeInstanceOf(ApiError)

    expect(refused).toHaveBeenCalledWith('jwt')
  })

  test('is not reported for a scheme that carries no bearer token', async () => {
    const refused = vi.fn()
    setUnauthorizedHandler(refused)

    await expect(
      apiFetch('user', '/users/me', {
        headers: { Authorization: 'Basic YWxleDpwdw==' },
      }),
    ).rejects.toBeInstanceOf(ApiError)

    // a 401 for credentials that are not a session says nothing about one
    expect(refused).not.toHaveBeenCalled()
  })
})

// PR #182 re-review (@Sinnez1): the handler runs before the throw, so a
// failure inside it used to surface in place of the ApiError that
// callers match on (err.status === 401, errorMessage())
describe('apiFetch: a failing unauthorized handler', () => {
  test("does not replace the response's ApiError", async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {})
    setUnauthorizedHandler(() => {
      throw new Error('boom')
    })
    setTokenSource(() => 'jwt')

    const error = await apiFetch('user', '/users/me').catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(errorMessage(error, 'fallback')).toBe('Token has expired')
    // the handler's own failure is not swallowed in silence
    expect(logged).toHaveBeenCalled()
  })
})
