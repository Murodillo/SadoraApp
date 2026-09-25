import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/client'
import type { AdminSession } from '../api/types'
import { AuthProvider, useAuth } from './AuthContext'

const owner: AdminSession = {
  accessToken: 'jwt-owner',
  expiresAt: '2026-09-12T00:00:00Z',
  name: 'Owner',
  email: 'owner@sadora.uz',
  role: 'OWNER',
}

function Probe() {
  const { session, signIn, signOut, can } = useAuth()
  return (
    <div>
      <output data-testid="who">{session?.email ?? 'signed out'}</output>
      <output data-testid="billing">{String(can(['OWNER', 'ADMIN']))}</output>
      <output data-testid="support-only">{String(can(['SUPPORT']))}</output>
      <button onClick={() => void signIn('owner@sadora.uz', 'pw', '123456')}>sign in</button>
      <button onClick={signOut}>sign out</button>
    </div>
  )
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

describe('AuthProvider', () => {
  it('signs in, stores the token for this tab only, and applies the role', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(owner), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('signed out')

    await userEvent.click(screen.getByRole('button', { name: 'sign in' }))

    expect(await screen.findByText('owner@sadora.uz')).toBeInTheDocument()
    expect(screen.getByTestId('billing')).toHaveTextContent('true')
    expect(screen.getByTestId('support-only')).toHaveTextContent('false')
    expect(tokenStore.read()).toBe('jwt-owner')
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ email: 'owner@sadora.uz', password: 'pw', totpCode: '123456' })
  })

  it('restores a session left in this tab', () => {
    tokenStore.write(owner.accessToken)
    tokenStore.writeSession(owner)
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('owner@sadora.uz')
  })

  it('ignores a stored session whose token is gone', () => {
    tokenStore.writeSession(owner)
    renderWithAuth()
    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
  })

  it('drops to sign-in when any request reports an expired session', () => {
    tokenStore.write(owner.accessToken)
    tokenStore.writeSession(owner)
    renderWithAuth()

    act(() => {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
    })

    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
  })

  it('signs out and forgets the token', async () => {
    tokenStore.write(owner.accessToken)
    tokenStore.writeSession(owner)
    renderWithAuth()

    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))

    expect(screen.getByTestId('who')).toHaveTextContent('signed out')
    expect(tokenStore.read()).toBeNull()
  })

  it('refuses to be used outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Probe />)).toThrow('useAuth must be used inside AuthProvider')
  })
})
