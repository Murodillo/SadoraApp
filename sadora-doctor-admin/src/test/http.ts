import { vi } from 'vitest'

/** One call the panel made, as a test wants to look at it. */
export interface Call {
  method: string
  path: string
  search: URLSearchParams
  body: unknown
  headers: Record<string, string>
}

/** A route's fixed answer: a Response, or anything served as JSON. */
type Reply = Response | object | string | number | boolean | null
type Handler = (call: Call) => Reply | Promise<Reply>

export function json(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function apiError(status: number, code: string, message: string, details?: Record<string, string>): Response {
  return json({ error: { code, message, ...(details ? { details } : {}) } }, status)
}

/**
 * A fake backend keyed by `"METHOD /path"` (no query string). A value is served as JSON
 * on every call; a function sees the call and answers — with a `Response` for a status
 * other than 200. A route nobody declared answers 404, so a stray request fails loudly.
 */
export function mockApi(routes: Record<string, Handler | Reply>) {
  const calls: Call[] = []
  const fetchMock = vi.fn(async (input: string | URL | Request, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://panel.test')
    const method = (init.method ?? 'GET').toUpperCase()
    const call: Call = {
      method,
      path: url.pathname,
      search: url.searchParams,
      body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      headers: { ...(init.headers as Record<string, string> | undefined) },
    }
    calls.push(call)
    const route = routes[`${method} ${url.pathname}`]
    if (route === undefined) return apiError(404, 'not_found', `No mock for ${method} ${url.pathname}`)
    const reply = typeof route === 'function' ? await (route as Handler)(call) : route
    // A declared Response is cloned, so a route can give the same answer more than once.
    return reply instanceof Response ? reply.clone() : json(reply)
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    fetchMock,
    calls,
    callsTo: (method: string, path: string) => calls.filter((call) => call.method === method && call.path === path),
  }
}
