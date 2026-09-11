import { describe, expect, it, vi } from 'vitest'
import { ApiFailure, query, request, SESSION_EXPIRED_EVENT, tokenStore } from './client'

function reply(status: number, body?: unknown): Response {
  const text = body === undefined ? null : typeof body === 'string' ? body : JSON.stringify(body)
  return new Response(text, { status })
}

/** Each entry is a response, or `{ reject }` for a fetch that throws. */
function stubFetch(...responses: Array<Response | { reject: unknown }>) {
  const fetchMock = vi.fn()
  for (const next of responses) {
    if (next instanceof Response) fetchMock.mockResolvedValueOnce(next)
    else fetchMock.mockRejectedValueOnce(next.reject)
  }
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  const error = await promise.then(
    () => undefined,
    (cause: unknown) => cause,
  )
  expect(error).toBeInstanceOf(ApiFailure)
  return error as ApiFailure
}

describe('request', () => {
  it('sends JSON with the operator token and returns the parsed body', async () => {
    tokenStore.write('op-token')
    const fetchMock = stubFetch(reply(200, { ok: true }))

    const result = await request<{ ok: boolean }>('/v1/admin/shop/products', { method: 'POST', body: { a: 1 } })

    expect(result).toEqual({ ok: true })
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/v1/admin/shop/products')
    expect(init.method).toBe('POST')
    expect(init.body).toBe('{"a":1}')
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json', Authorization: 'Bearer op-token' })
  })

  it('keeps the token off anonymous calls such as sign-in', async () => {
    tokenStore.write('stale-token')
    const fetchMock = stubFetch(reply(200, {}))

    await request('/v1/admin/auth/login', { method: 'POST', anonymous: true, body: {} })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.headers).not.toHaveProperty('Authorization')
  })

  it('treats 204 as success with no body', async () => {
    stubFetch(reply(204))
    await expect(request('/v1/admin/flags/x', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  // What a browser saw on staging when the API's CORS list lacked the tunnel origin:
  // Ktor answers 403 with an empty body, and the panel must still say something useful.
  it('turns a bare 403 into a readable failure', async () => {
    stubFetch(reply(403, ''))

    const failure = await failureOf(request('/v1/admin/auth/login', { method: 'POST', anonymous: true }))

    expect(failure.status).toBe(403)
    expect(failure.code).toBe('unexpected')
    expect(failure.message).toBe('Server xatosi (403)')
    expect(failure.isUnauthorized).toBe(false)
  })

  it('carries the server error envelope through', async () => {
    stubFetch(
      reply(422, {
        error: { code: 'validation_failed', message: 'Tekshiring', details: { slug: 'band' }, requestId: 'req-1' },
      }),
    )

    const failure = await failureOf(request('/v1/admin/content', { method: 'POST', body: {} }))

    expect(failure).toMatchObject({ code: 'validation_failed', message: 'Tekshiring', requestId: 'req-1', status: 422 })
    expect(failure.fields).toEqual({ slug: 'band' })
  })

  it('does not choke on a non-JSON error page from a proxy', async () => {
    stubFetch(reply(502, '<html>Bad gateway</html>'))
    const failure = await failureOf(request('/v1/admin/stats'))
    expect(failure.message).toBe('Server xatosi (502)')
  })

  it.each(['unauthorized', 'token_expired', 'token_revoked'])('signs the operator out on %s', async (code) => {
    tokenStore.write('op-token')
    tokenStore.writeSession({ role: 'OWNER' })
    const expired = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, expired)
    stubFetch(reply(401, { error: { code, message: 'no' } }))

    await failureOf(request('/v1/admin/stats'))

    expect(tokenStore.read()).toBeNull()
    expect(tokenStore.readSession()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
  })

  it('leaves the session alone when a sign-in attempt is refused', async () => {
    tokenStore.write('op-token')
    const expired = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, expired)
    stubFetch(reply(401, { error: { code: 'unauthorized', message: 'wrong password' } }))

    await failureOf(request('/v1/admin/auth/login', { method: 'POST', anonymous: true }))

    expect(tokenStore.read()).toBe('op-token')
    expect(expired).not.toHaveBeenCalled()
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
  })

  it('reports an unreachable server in words', async () => {
    stubFetch({ reject: new TypeError('Failed to fetch') })
    const failure = await failureOf(request('/v1/admin/stats'))
    expect(failure.code).toBe('network')
  })

  it('lets an aborted request stay an abort', async () => {
    const controller = new AbortController()
    controller.abort()
    const abort = new DOMException('aborted', 'AbortError')
    stubFetch({ reject: abort })
    await expect(request('/v1/admin/stats', { signal: controller.signal })).rejects.toBe(abort)
  })
})

describe('tokenStore', () => {
  it('keeps the token in sessionStorage so it dies with the tab', () => {
    tokenStore.write('t')
    expect(sessionStorage.getItem('sadora.admin.token')).toBe('t')
    expect(localStorage.length).toBe(0)
  })
})

describe('query', () => {
  it('drops empty filters and encodes the rest', () => {
    expect(query({ q: 'a b', page: 2, tier: '', status: undefined, from: null })).toBe('?q=a+b&page=2')
  })

  it('is empty when nothing is set', () => {
    expect(query({ q: '' })).toBe('')
  })
})
