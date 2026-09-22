import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom'
import { ApiFailure } from './api/client'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './auth/LoginPage'
import { ToastProvider } from './components/toast'
import { Shell } from './layout/Shell'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AuditPage } from './pages/AuditPage'
import { CommunityPage } from './pages/CommunityPage'
import { AiPage } from './pages/AiPage'
import { BillingPage } from './pages/BillingPage'
import { ContentPage } from './pages/ContentPage'
import { DashboardPage } from './pages/DashboardPage'
import { FeaturesPage } from './pages/FeaturesPage'
import { FlagsPage } from './pages/FlagsPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { RewardsPage } from './pages/RewardsPage'
import { ShopPage } from './pages/ShopPage'
import { SecurityPage } from './pages/SecurityPage'
import { UserCardPage } from './pages/UserCardPage'
import { UsersPage } from './pages/UsersPage'
import { WearablesPage } from './pages/WearablesPage'

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
        <ToastProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ToastProvider>
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
        <Route
          path="analytics"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <AnalyticsPage /> : <Navigate to="/" replace />}
        />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:id" element={<UserCardPage />} />
        <Route path="community" element={<CommunityPage />} />
        <Route path="content" element={<ContentPage />} />
        <Route path="billing" element={<BillingPage />} />
        <Route
          path="ai"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <AiPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="notifications"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <NotificationsPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="wearables"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <WearablesPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="rewards"
          element={can(['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST']) ? <RewardsPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="shop"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <ShopPage /> : <Navigate to="/" replace />}
        />
        <Route path="features" element={<FeaturesPage />} />
        <Route path="flags" element={<FlagsPage />} />
        <Route path="security" element={<SecurityPage />} />
        <Route path="audit" element={can(['OWNER']) ? <AuditPage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
