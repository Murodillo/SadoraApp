import type { ApiError } from './types'

/**
 * The API's origin, put in front of every `/v1/…` path. Blank (the default) calls this
 * page's own origin, which is what Vite's dev proxy answers; a deployed panel is built
 * with VITE_API_BASE because it and the API are separate hostnames there.
 */
export const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/+$/, '')

const TOKEN_KEY = 'sadora.admin.token'
const SESSION_KEY = 'sadora.admin.session'

/**
 * The panel holds its access token in `sessionStorage`, not `localStorage`: it dies with
 * the tab. An operator's token opens every subscription in the product, and a shared or
 * kiosk machine should not keep one lying around after the window closes.
 */
export const tokenStore = {
  read: () => sessionStorage.getItem(TOKEN_KEY),
  write: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(SESSION_KEY)
  },
  readSession: <T,>(): T | null => {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as T) : null
  },
  writeSession: (value: unknown) => sessionStorage.setItem(SESSION_KEY, JSON.stringify(value)),
}

/** A failure the UI can render. `fields` is populated for validation errors. */
export class ApiFailure extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly fields: Record<string, string> = {},
    readonly requestId?: string,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiFailure'
  }

  get isUnauthorized(): boolean {
    return this.code === 'unauthorized' || this.code === 'token_expired' || this.code === 'token_revoked'
  }
}

/** Raised on 401 so the app can drop back to the sign-in screen from anywhere. */
export const SESSION_EXPIRED_EVENT = 'sadora:session-expired'

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /** Sign-in itself must not trigger the global sign-out handler. */
  anonymous?: boolean
}

/**
 * The panel speaks Uzbek, and the server words its refusals in the language a request
 * asks for — left to the browser's own Accept-Language they would come back in Russian
 * or English.
 */
const UZBEK = { 'Accept-Language': 'uz' }

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, anonymous = false } = options
  const token = tokenStore.read()

  let response: Response
  try {
    response = await fetch(API_BASE + path, {
      method,
      signal,
      headers: {
        'Content-Type': 'application/json',
        ...UZBEK,
        ...(token && !anonymous ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    throw new ApiFailure('network', "Serverga ulanib bo'lmadi. Internet aloqasini tekshirib, qayta urinib ko'ring.")
  }

  if (response.status === 204) return undefined as T

  const payload = await response.text()
  const parsed = payload ? safeParse(payload) : null

  if (!response.ok) throw failureFrom(response.status, parsed, anonymous)

  return parsed as T
}

/**
 * An authenticated binary download — a doctor's diploma scan, say. Those endpoints need
 * the bearer header, so an `<img src>` cannot reach them; the caller turns the blob into
 * an object URL and revokes it when done.
 */
export async function requestBlob(path: string, options: { signal?: AbortSignal } = {}): Promise<Blob> {
  const token = tokenStore.read()
  let response: Response
  try {
    response = await fetch(API_BASE + path, {
      signal: options.signal,
      headers: { ...UZBEK, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    })
  } catch (cause) {
    if (options.signal?.aborted) throw cause
    throw new ApiFailure('network', "Serverga ulanib bo'lmadi. Internet aloqasini tekshirib, qayta urinib ko'ring.")
  }
  if (!response.ok) {
    const payload = await response.text()
    throw failureFrom(response.status, payload ? safeParse(payload) : null, false)
  }
  return response.blob()
}

/**
 * What the operator reads when the server sent no message of its own — a proxy's 502
 * page, a bare 403. Says what to do next; the status stays at the end for a bug report.
 */
export function fallbackMessage(status: number): string {
  if (status >= 500) return `Server hozir javob bermadi. Birozdan keyin qayta urinib ko'ring. (xato ${status})`
  return `So'rov bajarilmadi. Sahifani yangilab, qayta urinib ko'ring. (xato ${status})`
}

function failureFrom(status: number, parsed: unknown, anonymous: boolean): ApiFailure {
  const error = (parsed as { error?: ApiError } | null)?.error
  const failure = new ApiFailure(
    error?.code ?? 'unexpected',
    error?.message ?? fallbackMessage(status),
    error?.details ?? {},
    error?.requestId,
    status,
  )
  // The admin token is not refreshable — there is no refresh token in this realm on
  // purpose, so an expired one means signing in again, with the 2FA code.
  if (failure.isUnauthorized && !anonymous) {
    tokenStore.clear()
    window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
  }
  return failure
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/** Builds a query string, dropping empty values so filters stay out of the URL. */
export function query(params: Record<string, string | number | undefined | null>): string {
  const entries = Object.entries(params).filter(
    ([, value]) => value !== undefined && value !== null && value !== '',
  )
  return entries.length ? `?${new URLSearchParams(entries.map(([k, v]) => [k, String(v)]))}` : ''
}
