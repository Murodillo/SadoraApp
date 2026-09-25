import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/client'
import { deviceId } from '../api/device'
import { mockApi } from '../test/http'
import { AuthProvider, useAuth } from './AuthContext'

const challenge = {
  challengeId: 'ch-1',
  expiresAt: '2026-09-26T12:05:00Z',
  resendAfterSeconds: 60,
  attemptsLeft: 5,
}

const signedIn = {
  tokens: { accessToken: 'access-1', refreshToken: 'refresh-1', accessExpiresAt: 'x', refreshExpiresAt: 'y' },
  user: { id: 'user-1', name: 'Dilnoza', phone: '+998901234567' },
  entitlements: {},
  isNewUser: false,
}

function Probe() {
  const { session, requestCode, verifyCode, signOut } = useAuth()
  return (
    <div>
      <output data-testid="who">{session?.phone ?? 'signed out'}</output>
      <button onClick={() => void requestCode('+998901234567').then(() => verifyCode('ch-1', '123456'))}>sign in</button>
      <button onClick={signOut}>sign out</button>
    </div>
  )
}

function renderWithAuth(onSessionEnd?: () => void) {
  return render(
    <AuthProvider onSessionEnd={onSessionEnd}>
      <Probe />
    </AuthProvider>,
  )
}

describe('AuthProvider', () => {
  it('signs in with the phone code and keeps the pair for this tab only', async () => {
    const api = mockApi({
      'POST /v1/auth/otp/request': challenge,
      'POST /v1/auth/otp/verify': signedIn,
    })
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('signed out')

    await userEvent.click(screen.getByRole('button', { name: 'sign in' }))

    expect(await screen.findByText('+998901234567')).toBeInTheDocument()
    expect(api.callsTo('POST', '/v1/auth/otp/request')[0]!.body).toEqual({ phone: '+998901234567', language: 'uz' })
    const verify = api.callsTo('POST', '/v1/auth/otp/verify')[0]!
    expect(verify.body).toMatchObject({
      challengeId: 'ch-1',
      code: '123456',
      device: { deviceId: deviceId(), platform: 'web' },
    })
    expect(verify.headers).not.toHaveProperty('Authorization')
    expect(tokenStore.readAccess()).toBe('access-1')
    expect(tokenStore.readRefresh()).toBe('refresh-1')
  })

  it('restores a session left in this tab', () => {
    tokenStore.writeTokens({ accessToken: 'a', refreshToken: 'r' })
    tokenStore.writeSession({ userId: 'user-1', phone: '+998901234567' })
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('+998901234567')
  })

  it('ignores a stored session whose tokens are gone', () => {
    tokenStore.writeSession({ userId: 'user-1', phone: '+998901234567' })
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
  })

  it('drops to sign-in when a renewal is refused anywhere', () => {
    tokenStore.writeTokens({ accessToken: 'a', refreshToken: 'r' })
    tokenStore.writeSession({ userId: 'user-1', phone: '+998901234567' })
    const ended = vi.fn()
    renderWithAuth(ended)

    act(() => {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
    })

    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
    expect(ended).toHaveBeenCalledOnce()
  })

  it('signs out at once and revokes the refresh token on the server', async () => {
    tokenStore.writeTokens({ accessToken: 'a', refreshToken: 'r' })
    tokenStore.writeSession({ userId: 'user-1', phone: '+998901234567' })
    const api = mockApi({ 'POST /v1/auth/logout': { ok: true } })
    const ended = vi.fn()
    renderWithAuth(ended)

    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))

    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
    expect(tokenStore.readRefresh()).toBeNull()
    expect(ended).toHaveBeenCalledOnce()
    expect(api.callsTo('POST', '/v1/auth/logout')[0]!.body).toEqual({ refreshToken: 'r' })
  })

  it('refuses to be used outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    // React rethrows through a window error event; cancelled, jsdom does not print it.
    const quiet = (event: ErrorEvent) => event.preventDefault()
    window.addEventListener('error', quiet)
    try {
      expect(() => render(<Probe />)).toThrow('useAuth must be used inside AuthProvider')
    } finally {
      window.removeEventListener('error', quiet)
    }
  })
})
