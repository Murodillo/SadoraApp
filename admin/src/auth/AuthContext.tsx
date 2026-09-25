import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { request, SESSION_EXPIRED_EVENT, tokenStore } from '../api/client'
import type { AdminRole, AdminSession } from '../api/types'

interface AuthValue {
  session: AdminSession | null
  signIn: (email: string, password: string, totpCode?: string) => Promise<void>
  signOut: () => void
  /** Whether the signed-in role may reach a page. Mirrors the server's route guards. */
  can: (roles: AdminRole[]) => boolean
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({
  children,
  onSessionEnd,
}: {
  children: ReactNode
  /**
   * Runs whenever a session ends, however it ends. The app clears the query cache
   * here: on a shared machine the next operator's first paint used to show the previous
   * one's cached user list — phones, emails — until the refetch landed.
   */
  onSessionEnd?: () => void
}) {
  const [session, setSession] = useState<AdminSession | null>(() =>
    tokenStore.read() ? tokenStore.readSession<AdminSession>() : null,
  )

  // Any 401 anywhere in the app drops back to sign-in. The admin realm has no refresh
  // token by design — re-entering the 2FA code is the point.
  useEffect(() => {
    const handler = () => {
      setSession(null)
      onSessionEnd?.()
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handler)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler)
  }, [onSessionEnd])

  const signIn = useCallback(async (email: string, password: string, totpCode?: string) => {
    const result = await request<AdminSession>('/v1/admin/auth/login', {
      method: 'POST',
      anonymous: true,
      body: { email, password, ...(totpCode ? { totpCode } : {}) },
    })
    tokenStore.write(result.accessToken)
    tokenStore.writeSession(result)
    setSession(result)
  }, [])

  const signOut = useCallback(() => {
    tokenStore.clear()
    setSession(null)
    onSessionEnd?.()
  }, [onSessionEnd])

  const value = useMemo<AuthValue>(
    () => ({
      session,
      signIn,
      signOut,
      can: (roles) => Boolean(session && roles.includes(session.role)),
    }),
    [session, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
