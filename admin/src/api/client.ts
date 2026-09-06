import type { ApiError } from './types'

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

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, anonymous = false } = options
  const token = tokenStore.read()

  let response: Response
  try {
    response = await fetch(path, {
      method,
      signal,
      headers: {
        'Content-Type': 'application/json',
        ...(token && !anonymous ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    throw new ApiFailure('network', 'Serverga ulanib bo‘lmadi. Backend ishga tushganmi?')
  }

  if (response.status === 204) return undefined as T

  const payload = await response.text()
  const parsed = payload ? safeParse(payload) : null

  if (!response.ok) {
    const error = (parsed as { error?: ApiError } | null)?.error
    const failure = new ApiFailure(
      error?.code ?? 'unexpected',
      error?.message ?? `Server xatosi (${response.status})`,
      error?.details ?? {},
      error?.requestId,
      response.status,
    )
    // The admin token is not refreshable — there is no refresh token in this realm on
    // purpose, so an expired one means signing in again, with the 2FA code.
    if (failure.isUnauthorized && !anonymous) {
      tokenStore.clear()
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
    }
    throw failure
  }

  return parsed as T
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
