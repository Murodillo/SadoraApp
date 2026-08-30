import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom'
import { ApiFailure } from './api/client'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './auth/LoginPage'
import { Shell } from './layout/Shell'
import { AuditPage } from './pages/AuditPage'
import { DashboardPage } from './pages/DashboardPage'
import { FeaturesPage } from './pages/FeaturesPage'
import { FlagsPage } from './pages/FlagsPage'
import { UserCardPage } from './pages/UserCardPage'
import { UsersPage } from './pages/UsersPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // Retrying a 401 or a 403 only delays the sign-in screen; retry transport
      // problems and nothing else.
      retry: (failureCount, error) =>
        error instanceof ApiFailure && error.code === 'network' && failureCount < 2,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}

/**
 * Route guards mirror the server's, which is the only place they are enforced. Hiding a
 * page the API would refuse is a courtesy to the operator, not a security boundary.
 */
function AppRoutes() {
  const { session, can } = useAuth()
  if (!session) return <LoginPage />

  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<DashboardPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:id" element={<UserCardPage />} />
        <Route path="features" element={<FeaturesPage />} />
        <Route path="flags" element={<FlagsPage />} />
        <Route path="audit" element={can(['OWNER']) ? <AuditPage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
