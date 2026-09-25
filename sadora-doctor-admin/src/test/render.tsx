import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { tokenStore } from '../api/client'
import { createQueryClient } from '../api/queryClient'
import type { CommunityComment, CommunityPost, DoctorAccount } from '../api/types'
import { AuthProvider } from '../auth/AuthContext'
import { ToastProvider } from '../components/toast'

/** The app's providers around `ui`, with a memory router and a query client that never retries. */
export function renderApp(ui: ReactNode, { route = '/' }: { route?: string } = {}) {
  const client = createQueryClient({ retry: false })
  const result = render(
    <QueryClientProvider client={client}>
      <AuthProvider onSessionEnd={() => client.clear()}>
        <ToastProvider>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>,
  )
  return { ...result, client }
}

/** A session in this tab, as a completed sign-in leaves it. */
export function signIn(): void {
  tokenStore.writeTokens({ accessToken: 'access-1', refreshToken: 'refresh-1' })
  tokenStore.writeSession({ userId: 'user-1', phone: '+998901234567' })
}

export function doctorAccount(overrides: Partial<DoctorAccount> = {}): DoctorAccount {
  return {
    status: 'approved',
    profileId: 'doc-1',
    fullName: 'Dilnoza Karimova',
    specialty: 'gynecologist',
    workplace: 'Toshkent, 1-son klinika',
    experienceYears: 12,
    licenseNumber: 'LIC-001',
    bio: null,
    documentCount: 2,
    reviewNote: null,
    submittedAt: '2026-09-01T09:00:00Z',
    reviewedAt: '2026-09-02T09:00:00Z',
    ...overrides,
  }
}

export function question(id: string, body: string, overrides: Partial<CommunityPost> = {}): CommunityPost {
  return {
    id,
    topic: 'cycle',
    alias: `Lola ${id}`,
    tint: 2,
    body,
    createdAt: new Date(Date.now() - 30 * 60_000).toISOString(),
    likeCount: 1,
    commentCount: 0,
    doctorAnswers: 0,
    ...overrides,
  }
}

export function comment(id: string, postId: string, body: string, overrides: Partial<CommunityComment> = {}): CommunityComment {
  return {
    id,
    postId,
    alias: 'Nargiza',
    tint: 1,
    body,
    createdAt: new Date(Date.now() - 5 * 60_000).toISOString(),
    ...overrides,
  }
}
