import { deviceId } from './device'
import type { ApiError, AuthSession, TokenPair } from './types'

const ACCESS_KEY = 'sadora.doctor.access'
const REFRESH_KEY = 'sadora.doctor.refresh'
const SESSION_KEY = 'sadora.doctor.session'

const REFRESH_PATH = '/v1/auth/refresh'
const LOGOUT_PATH = '/v1/auth/logout'

/** The server stamps the rotated refresh token with this, as it does for the phone. */
const DEVICE_HEADER = 'X-Device-Id'

/**
 * Both tokens live in `sessionStorage`, not `localStorage`: they die with the tab.
 *
 * The staff panel does this for its operator token, and the reason carries over with
 * more force — a doctor answers from a clinic computer that the next person at the desk
 * also uses, and her session writes under her real name with a check mark beside it. The
 * refresh token sits beside the access token for the same reason; closing the window
 * signs her out, and that is the price, paid on purpose.
 */
export const tokenStore = {
  readAccess: () => sessionStorage.getItem(ACCESS_KEY),
  readRefresh: () => sessionStorage.getItem(REFRESH_KEY),
  writeTokens: (pair: Pick<TokenPair, 'accessToken' | 'refreshToken'>) => {
    sessionStorage.setItem(ACCESS_KEY, pair.accessToken)
    sessionStorage.setItem(REFRESH_KEY, pair.refreshToken)
  },
  clear: () => {
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
    sessionStorage.removeItem(SESSION_KEY)
  },
  readSession: <T,>(): T | null => {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as T
    } catch {
      return null
    }
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
    return this.status === 401
  }
}

/** Raised when the session cannot be renewed, so the app can drop back to sign-in from anywhere. */
export const SESSION_EXPIRED_EVENT = 'sadora:doctor-session-expired'

const OFFLINE_MESSAGE = "Serverga ulanib bo'lmadi. Internet aloqasini tekshiring."

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /** Sign-in itself: no token is sent, and a refusal must not end a session. */
  anonymous?: boolean
  headers?: Record<string, string>
}

/**
 * One call to the API. A 401 on an authenticated call renews the token pair once and
 * repeats the call; if the renewal is refused the session ends everywhere at once.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { anonymous = false } = options
  let sentWith = anonymous ? null : tokenStore.readAccess()
  let response = await send(path, options, sentWith)

  if (response.status === 401 && !anonymous) {
    // Another call may have renewed the pair while this one was on the wire; the new
    // access token is then simply tried rather than spending the refresh token again.
    const current = tokenStore.readAccess()
    const renewed = current !== null && current !== sentWith ? true : await renewSession()
    if (renewed) {
      sentWith = tokenStore.readAccess()
      response = await send(path, options, sentWith)
    }
  }

  return read<T>(response, anonymous ? undefined : sentWith)
}

let renewal: Promise<boolean> | null = null

/**
 * Trades the refresh token for a new pair. Every call that hits a 401 at the same moment
 * waits on the same renewal: the server rotates the refresh token on each use, so two
 * renewals racing would spend it twice.
 *
 * Resolves false when the server refuses — the session is then over and has been ended.
 * A network failure or a 5xx rejects instead and leaves the session alone: the next
 * request can try again, and a flaky connection is no reason to throw a doctor out.
 */
export function renewSession(): Promise<boolean> {
  renewal ??= renew().finally(() => {
    renewal = null
  })
  return renewal
}

async function renew(): Promise<boolean> {
  const refreshToken = tokenStore.readRefresh()
  if (!refreshToken) return false
  const staleAccess = tokenStore.readAccess()

  const response = await send(
    REFRESH_PATH,
    { method: 'POST', body: { refreshToken }, headers: { [DEVICE_HEADER]: deviceId() } },
    null,
  )
  // Signed out, or signed in afresh, while the renewal was on the wire: its answer
  // belongs to a session that no longer exists and must not be written back.
  if (tokenStore.readRefresh() !== refreshToken) return false

  const payload = await response.text()
  const parsed = payload ? safeParse(payload) : null
  const refused = response.status === 400 || response.status === 401 || response.status === 403
  if (!response.ok && !refused) throw failureFrom(response.status, parsed)

  const pair = response.ok ? tokensOf(parsed) : null
  if (!pair) {
    endSession(staleAccess)
    return false
  }
  tokenStore.writeTokens(pair)
  return true
}

/**
 * Signs this tab out: the tokens are forgotten at once, then the refresh token is revoked
 * on the server so it cannot be replayed from wherever it might have been copied.
 *
 * Self-contained on purpose — it works on the tokens it captured, never on the store, so
 * a renewal finishing elsewhere or the next sign-in cannot be touched by it.
 */
export async function logout(): Promise<void> {
  const access = tokenStore.readAccess()
  const refreshToken = tokenStore.readRefresh()
  tokenStore.clear()
  if (!refreshToken) return
  try {
    const response = await send(LOGOUT_PATH, { method: 'POST', body: { refreshToken } }, access)
    if (response.status !== 401) return
    // The access token lapsed while she sat idle. Renew once, only to revoke what the
    // renewal hands back — revoking the spent token would leave its successor alive.
    const renewed = await send(
      REFRESH_PATH,
      { method: 'POST', body: { refreshToken }, headers: { [DEVICE_HEADER]: deviceId() } },
      null,
    )
    const pair = renewed.ok ? tokensOf(safeParse(await renewed.text())) : null
    if (pair) await send(LOGOUT_PATH, { method: 'POST', body: { refreshToken: pair.refreshToken } }, pair.accessToken)
  } catch {
    // Offline. The tokens are already gone from this tab, and the server lets the
    // refresh token run out on its own.
  }
}

async function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const { method = 'GET', body, signal, headers = {} } = options
  try {
    return await fetch(path, {
      method,
      signal,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    throw new ApiFailure('network', OFFLINE_MESSAGE)
  }
}

/** `sentWith` is the access token of the last attempt, or undefined for an anonymous call. */
async function read<T>(response: Response, sentWith: string | null | undefined): Promise<T> {
  if (response.status === 204) return undefined as T
  const payload = await response.text()
  const parsed = payload ? safeParse(payload) : null
  if (!response.ok) {
    const failure = failureFrom(response.status, parsed)
    if (response.status === 401 && sentWith !== undefined) endSession(sentWith)
    throw failure
  }
  return parsed as T
}

/**
 * Forgets the tokens and tells the app. `staleAccess` is the token the failing call was
 * made with: when the store already holds a different one, a newer session is in place
 * and this failure belongs to the old one, so nothing is cleared.
 */
function endSession(staleAccess: string | null): void {
  const current = tokenStore.readAccess()
  if (current !== null && current !== staleAccess) return
  const hadSession = current !== null || tokenStore.readRefresh() !== null
  tokenStore.clear()
  if (hadSession) window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
}

/**
 * The server answers a refresh with a whole `AuthSession` (the pair under `tokens`); a
 * bare `TokenPair` is accepted as well, so the panel does not break if that is trimmed.
 */
function tokensOf(payload: unknown): TokenPair | null {
  const candidate = ((payload as Partial<AuthSession> | null)?.tokens ?? payload) as Partial<TokenPair> | null
  return candidate && typeof candidate.accessToken === 'string' && typeof candidate.refreshToken === 'string'
    ? (candidate as TokenPair)
    : null
}

function failureFrom(status: number, parsed: unknown): ApiFailure {
  const error = (parsed as { error?: ApiError } | null)?.error
  return new ApiFailure(
    error?.code ?? 'unexpected',
    error?.message ?? `Server xatosi (${status})`,
    error?.details ?? {},
    error?.requestId,
    status,
  )
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

/** The words to show for any failure, in a toast or a notice. */
export function messageOf(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return 'Kutilmagan xatolik'
}

/** Per-field validation messages the server attached, keyed by field name. */
export function fieldsOf(error: unknown): Record<string, string> {
  return error instanceof ApiFailure ? error.fields : {}
}
