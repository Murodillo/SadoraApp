import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { logout, request, SESSION_EXPIRED_EVENT, tokenStore } from '../api/client'
import { deviceInfo } from '../api/device'
import type { AuthSession, OtpChallenge } from '../api/types'

/** What the panel remembers about who signed in, beside the tokens. */
export interface DoctorSession {
  userId: string
  /** E.164, as the server has it — the number she must also use in Sadora Doctor. */
  phone: string | null
}

interface AuthValue {
  session: DoctorSession | null
  /** Sends the code by SMS. `phone` is E.164 (`+998901234567`). */
  requestCode: (phone: string) => Promise<OtpChallenge>
  /** Spends the code on its challenge and starts the session. */
  verifyCode: (challengeId: string, code: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({
  children,
  onSessionEnd,
}: {
  children: ReactNode
  /**
   * Runs whenever a session ends, however it ends. The app clears the query cache here,
   * so the next doctor at the same desk never sees the previous one's questions or
   * profile for the moment before a refetch lands.
   */
  onSessionEnd?: () => void
}) {
  const [session, setSession] = useState<DoctorSession | null>(() =>
    tokenStore.readRefresh() || tokenStore.readAccess() ? tokenStore.readSession<DoctorSession>() : null,
  )

  // A renewal the server refused, anywhere in the app, drops back to sign-in.
  useEffect(() => {
    const handler = () => {
      setSession(null)
      onSessionEnd?.()
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handler)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler)
  }, [onSessionEnd])

  const requestCode = useCallback(
    (phone: string) =>
      request<OtpChallenge>('/v1/auth/otp/request', {
        method: 'POST',
        anonymous: true,
        body: { phone, language: 'uz' },
      }),
    [],
  )

  const verifyCode = useCallback(async (challengeId: string, code: string) => {
    const result = await request<AuthSession>('/v1/auth/otp/verify', {
      method: 'POST',
      anonymous: true,
      body: { challengeId, code, device: deviceInfo() },
    })
    const next: DoctorSession = { userId: result.user.id, phone: result.user.phone ?? null }
    tokenStore.writeTokens(result.tokens)
    tokenStore.writeSession(next)
    setSession(next)
  }, [])

  const signOut = useCallback(() => {
    // The tokens are forgotten synchronously inside logout(); the revocation call then
    // runs on its own, and the screen does not wait for the network to say goodbye.
    void logout()
    setSession(null)
    onSessionEnd?.()
  }, [onSessionEnd])

  const value = useMemo<AuthValue>(
    () => ({ session, requestCode, verifyCode, signOut }),
    [session, requestCode, verifyCode, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
