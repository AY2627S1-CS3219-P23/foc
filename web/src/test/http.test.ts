// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-14, PR #182 re-review (@Sinnez1).
// Scope: unit tests for apiFetch's two contracts around the
// unauthorized handler (issue #178) — which Authorization headers count
// as a sent bearer token, and that a failing handler does not replace
// the ApiError a caller was promised. The feature-level cases live in
// auth.test.tsx; these two are easier to state against apiFetch itself.
// 2026-10-16, Claude Code (Opus 5), PR #180 main sync: the body-optional
// 202 and the `auth: false` opt-out get cases here too — issue #94 added
// the first to apiFetch, and #182 the second, in the same two releases.
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

// issue #94: POST /auth/password-reset answers an empty 202 on purpose
// (anything it said would reveal whether the address has an account),
// while sign-up's 202 carries its timings — so 202 cannot share 204's
// skip-the-parse branch, and a parse failure there is an absent body
// rather than an error
describe('apiFetch: a 202', () => {
  function accepted(body: unknown) {
    return {
      ok: true,
      status: 202,
      headers: new Headers(),
      json: async () =>
        body === undefined
          ? // what fetch does for a bodyless response
            Promise.reject(new SyntaxError('Unexpected end of JSON input'))
          : body,
    }
  }

  test('with no body resolves undefined instead of throwing', async () => {
    fetchMock.mockResolvedValue(accepted(undefined))

    await expect(
      apiFetch('user', '/auth/password-reset', { method: 'POST' }),
    ).resolves.toBeUndefined()
  })

  test('with a body still resolves it (sign-up)', async () => {
    fetchMock.mockResolvedValue(accepted({ expiresInSeconds: 600 }))

    await expect(
      apiFetch('user', '/auth/signup', { method: 'POST' }),
    ).resolves.toEqual({ expiresInSeconds: 600 })
  })
})

// PR #182's opt-out, pinned at the apiFetch level: the credential
// endpoints (login, and the two password-reset routes since PR #180)
// must not carry a stored session
describe('apiFetch: auth: false', () => {
  test('leaves the bearer token off the request', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
      json: async () => undefined,
    })
    setTokenSource(() => 'jwt')

    await apiFetch('user', '/auth/password-reset', {
      method: 'POST',
      auth: false,
    })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).has('Authorization')).toBe(false)
  })
})
