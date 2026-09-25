import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { apiError, json, mockApi } from '../test/http'
import { ApiFailure, fieldsOf, logout, messageOf, query, request, SESSION_EXPIRED_EVENT, tokenStore } from './client'
import { deviceId } from './device'

const REFRESH = 'POST /v1/auth/refresh'

/** What the server's refresh answers: a whole AuthSession, the pair under `tokens`. */
function renewedSession(access: string, refresh: string) {
  return {
    tokens: { accessToken: access, refreshToken: refresh, accessExpiresAt: '2026-09-26T12:15:00Z', refreshExpiresAt: '2026-10-26T12:00:00Z' },
    user: { id: 'user-1', name: 'Dilnoza', phone: '+998901234567' },
    entitlements: {},
    isNewUser: false,
  }
}

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  const error = await promise.then(
    () => undefined,
    (cause: unknown) => cause,
  )
  expect(error).toBeInstanceOf(ApiFailure)
  return error as ApiFailure
}

function bearer(headers: Record<string, string>): string | undefined {
  return headers.Authorization?.replace('Bearer ', '')
}

let expired: Mock<(event: Event) => void>

beforeEach(() => {
  expired = vi.fn<(event: Event) => void>()
  window.addEventListener(SESSION_EXPIRED_EVENT, expired)
})

afterEach(() => {
  window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
})

describe('request', () => {
  it('sends JSON with the access token and returns the parsed body', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const api = mockApi({ 'PUT /v1/doctor/me': { status: 'approved' } })

    const result = await request('/v1/doctor/me', { method: 'PUT', body: { bio: 'Salom' } })

    expect(result).toEqual({ status: 'approved' })
    const [call] = api.calls
    expect(call).toMatchObject({ method: 'PUT', path: '/v1/doctor/me', body: { bio: 'Salom' } })
    expect(call!.headers).toMatchObject({ 'Content-Type': 'application/json', Authorization: 'Bearer access-1' })
  })

  it('treats 204 as success with no body', async () => {
    mockApi({ 'DELETE /v1/x': () => new Response(null, { status: 204 }) })
    await expect(request('/v1/x', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('carries the server error envelope through, details and all', async () => {
    mockApi({
      'POST /v1/community/posts': json(
        { error: { code: 'validation_failed', message: 'Tekshiring', details: { body: 'Kamida 2 ta belgi' }, requestId: 'req-1' } },
        422,
      ),
    })

    const failure = await failureOf(request('/v1/community/posts', { method: 'POST', body: {} }))

    expect(failure).toMatchObject({ code: 'validation_failed', message: 'Tekshiring', requestId: 'req-1', status: 422 })
    expect(fieldsOf(failure)).toEqual({ body: 'Kamida 2 ta belgi' })
    expect(messageOf(failure)).toBe('Tekshiring')
  })

  it('does not choke on a non-JSON error page from a proxy', async () => {
    mockApi({ 'GET /v1/doctor/me': () => new Response('<html>Bad gateway</html>', { status: 502 }) })
    const failure = await failureOf(request('/v1/doctor/me'))
    expect(failure.message).toBe('Server xatosi (502)')
    expect(failure.code).toBe('unexpected')
  })

  it('reports an unreachable server in words', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const failure = await failureOf(request('/v1/doctor/me'))
    expect(failure.code).toBe('network')
    expect(failure.message).toMatch(/Serverga ulanib/)
  })

  it('lets an aborted request stay an abort', async () => {
    const controller = new AbortController()
    controller.abort()
    const abort = new DOMException('aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abort))
    await expect(request('/v1/doctor/me', { signal: controller.signal })).rejects.toBe(abort)
  })

  describe('anonymous calls (sign-in)', () => {
    it('never carry a token, never renew and never end a session', async () => {
      tokenStore.writeTokens({ accessToken: 'stale', refreshToken: 'refresh-1' })
      const api = mockApi({
        'POST /v1/auth/otp/verify': apiError(401, 'otp_invalid', "Kod noto'g'ri"),
      })

      const failure = await failureOf(request('/v1/auth/otp/verify', { method: 'POST', anonymous: true, body: {} }))

      expect(failure.code).toBe('otp_invalid')
      expect(api.calls).toHaveLength(1)
      expect(api.calls[0]!.headers).not.toHaveProperty('Authorization')
      expect(tokenStore.readRefresh()).toBe('refresh-1')
      expect(expired).not.toHaveBeenCalled()
    })
  })
})

describe('token renewal', () => {
  it('renews the pair on a 401 and repeats the call once with the new token', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const api = mockApi({
      'GET /v1/doctor/me': (call) =>
        bearer(call.headers) === 'access-2' ? { status: 'approved' } : apiError(401, 'unauthorized', 'Avtorizatsiya talab qilinadi'),
      [REFRESH]: renewedSession('access-2', 'refresh-2'),
    })

    await expect(request('/v1/doctor/me')).resolves.toEqual({ status: 'approved' })

    expect(api.calls.map((call) => `${call.method} ${call.path} ${bearer(call.headers) ?? '-'}`)).toEqual([
      'GET /v1/doctor/me access-1',
      'POST /v1/auth/refresh -',
      'GET /v1/doctor/me access-2',
    ])
    const [refresh] = api.callsTo('POST', '/v1/auth/refresh')
    expect(refresh!.body).toEqual({ refreshToken: 'refresh-1' })
    expect(refresh!.headers['X-Device-Id']).toBe(deviceId())
    expect(tokenStore.readAccess()).toBe('access-2')
    expect(tokenStore.readRefresh()).toBe('refresh-2')
    expect(expired).not.toHaveBeenCalled()
  })

  it('also accepts a bare TokenPair from the refresh endpoint', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    mockApi({
      'GET /v1/x': (call) => (bearer(call.headers) === 'access-2' ? { ok: true } : apiError(401, 'unauthorized', 'no')),
      [REFRESH]: renewedSession('access-2', 'refresh-2').tokens,
    })

    await expect(request('/v1/x')).resolves.toEqual({ ok: true })
    expect(tokenStore.readRefresh()).toBe('refresh-2')
  })

  it('spends the refresh token once when several calls hit a 401 together', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const api = mockApi({
      'GET /v1/a': (call) => (bearer(call.headers) === 'access-2' ? { a: 1 } : apiError(401, 'unauthorized', 'no')),
      'GET /v1/b': (call) => (bearer(call.headers) === 'access-2' ? { b: 2 } : apiError(401, 'unauthorized', 'no')),
      [REFRESH]: async () => {
        await gate
        return renewedSession('access-2', 'refresh-2')
      },
    })

    const both = Promise.all([request('/v1/a'), request('/v1/b')])
    await vi.waitFor(() => expect(api.callsTo('POST', '/v1/auth/refresh')).toHaveLength(1))
    release()

    await expect(both).resolves.toEqual([{ a: 1 }, { b: 2 }])
    expect(api.callsTo('POST', '/v1/auth/refresh')).toHaveLength(1)
  })

  it('signs out when the server refuses the renewal', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    tokenStore.writeSession({ userId: 'user-1' })
    const api = mockApi({
      'GET /v1/doctor/me': apiError(401, 'unauthorized', 'Avtorizatsiya talab qilinadi'),
      [REFRESH]: apiError(401, 'token_revoked', 'Sessiya topilmadi'),
    })

    const failure = await failureOf(request('/v1/doctor/me'))

    expect(failure.status).toBe(401)
    expect(failure.isUnauthorized).toBe(true)
    expect(api.callsTo('GET', '/v1/doctor/me')).toHaveLength(1)
    expect(tokenStore.readAccess()).toBeNull()
    expect(tokenStore.readRefresh()).toBeNull()
    expect(tokenStore.readSession()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('signs out when the repeated call is refused too', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const api = mockApi({
      'GET /v1/doctor/me': apiError(401, 'unauthorized', 'no'),
      [REFRESH]: renewedSession('access-2', 'refresh-2'),
    })

    await failureOf(request('/v1/doctor/me'))

    expect(api.callsTo('GET', '/v1/doctor/me')).toHaveLength(2)
    expect(api.callsTo('POST', '/v1/auth/refresh')).toHaveLength(1)
    expect(tokenStore.readRefresh()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('signs out without calling refresh when there is no refresh token', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    sessionStorage.removeItem('sadora.doctor.refresh')
    const api = mockApi({ 'GET /v1/doctor/me': apiError(401, 'unauthorized', 'no') })

    await failureOf(request('/v1/doctor/me'))

    expect(api.callsTo('POST', '/v1/auth/refresh')).toHaveLength(0)
    expect(tokenStore.readAccess()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('keeps the session when the renewal fails for a reason that is not a refusal', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    mockApi({
      'GET /v1/doctor/me': apiError(401, 'unauthorized', 'no'),
      [REFRESH]: apiError(503, 'internal_error', "Server vaqtincha ishlamayapti"),
    })

    const failure = await failureOf(request('/v1/doctor/me'))

    expect(failure.status).toBe(503)
    expect(tokenStore.readRefresh()).toBe('refresh-1')
    expect(expired).not.toHaveBeenCalled()
  })

  it('does not write back a renewal that finished after she signed out', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    mockApi({
      'GET /v1/doctor/me': apiError(401, 'unauthorized', 'no'),
      [REFRESH]: () => {
        tokenStore.clear() // she pressed "Chiqish" while the renewal was on the wire
        return renewedSession('access-2', 'refresh-2')
      },
    })

    await failureOf(request('/v1/doctor/me'))

    expect(tokenStore.readAccess()).toBeNull()
    expect(tokenStore.readRefresh()).toBeNull()
  })
})

describe('logout', () => {
  it('forgets the tokens at once and revokes the refresh token', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const api = mockApi({ 'POST /v1/auth/logout': { ok: true } })

    const done = logout()
    expect(tokenStore.readAccess()).toBeNull()
    await done

    const [call] = api.callsTo('POST', '/v1/auth/logout')
    expect(call!.body).toEqual({ refreshToken: 'refresh-1' })
    expect(bearer(call!.headers)).toBe('access-1')
  })

  it('renews a lapsed access token only to revoke the renewed refresh token', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const api = mockApi({
      'POST /v1/auth/logout': (call) => (bearer(call.headers) === 'access-2' ? { ok: true } : apiError(401, 'unauthorized', 'no')),
      [REFRESH]: renewedSession('access-2', 'refresh-2'),
    })

    await logout()

    expect(api.callsTo('POST', '/v1/auth/logout').map((call) => call.body)).toEqual([
      { refreshToken: 'refresh-1' },
      { refreshToken: 'refresh-2' },
    ])
    expect(tokenStore.readRefresh()).toBeNull()
    expect(expired).not.toHaveBeenCalled()
  })

  it('still signs out offline', async () => {
    tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(logout()).resolves.toBeUndefined()
    expect(tokenStore.readRefresh()).toBeNull()
  })

  it('does nothing on the wire without a session', async () => {
    const api = mockApi({})
    await logout()
    expect(api.calls).toHaveLength(0)
  })
})

describe('tokenStore', () => {
  it('keeps the tokens in sessionStorage so they die with the tab', () => {
    tokenStore.writeTokens({ accessToken: 'a', refreshToken: 'r' })
    expect(sessionStorage.getItem('sadora.doctor.access')).toBe('a')
    expect(sessionStorage.getItem('sadora.doctor.refresh')).toBe('r')
    expect(localStorage.length).toBe(0)
  })

  it('survives a corrupted session entry', () => {
    sessionStorage.setItem('sadora.doctor.session', '{not json')
    expect(tokenStore.readSession()).toBeNull()
  })
})

describe('query', () => {
  it('drops empty filters and encodes the rest', () => {
    expect(query({ limit: 100, topic: undefined, q: 'a b', x: '', y: null })).toBe('?limit=100&q=a+b')
  })

  it('is empty when nothing is set', () => {
    expect(query({ topic: undefined })).toBe('')
  })
})
