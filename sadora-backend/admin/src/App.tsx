import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom'
import { ApiFailure } from './api/client'
import { useAdminMe } from './api/hooks'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './auth/LoginPage'
import { ErrorBoundary } from './components/ErrorBoundary'
import { Loading } from './components/ui'
import { ToastProvider } from './components/toast'
import { Shell } from './layout/Shell'
import { PM_ROLES } from './layout/nav'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AppReleasePage } from './pages/AppReleasePage'
import { AuditPage } from './pages/AuditPage'
import { CommunityPage } from './pages/CommunityPage'
import { AiPage } from './pages/AiPage'
import { BillingPage } from './pages/BillingPage'
import { CampaignsPage } from './pages/CampaignsPage'
import { ConsultationsPage } from './pages/ConsultationsPage'
import { ContentPage } from './pages/ContentPage'
import { DashboardPage } from './pages/DashboardPage'
import { DoctorsPage } from './pages/DoctorsPage'
import { FeaturesPage } from './pages/FeaturesPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { OperatorsPage } from './pages/OperatorsPage'
import { PromoVideoPage } from './pages/PromoVideoPage'
import { RewardsPage } from './pages/RewardsPage'
import { ShopPage } from './pages/ShopPage'
import { SecurityPage } from './pages/SecurityPage'
import { SystemPage } from './pages/SystemPage'
import { UserCardPage } from './pages/UserCardPage'
import { UsersPage } from './pages/UsersPage'
import { WearablesPage } from './pages/WearablesPage'
import { BoardPage } from './pm/BoardPage'
import { DocsPage } from './pm/DocsPage'
import { EpicsPage } from './pm/EpicsPage'
import { TaskPage } from './pm/TaskPage'
import { TokensPage } from './pm/TokensPage'

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
      <AuthProvider onSessionEnd={() => queryClient.clear()}>
        <ToastProvider>
          <BrowserRouter>
            <ErrorBoundary>
              <AppRoutes />
            </ErrorBoundary>
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
  const { session } = useAuth()
  if (!session) return <LoginPage />
  return <SignedInRoutes />
}

const pmRoutes = (
  <>
    <Route path="pm" element={<BoardPage />} />
    <Route path="pm/epics" element={<EpicsPage />} />
    <Route path="pm/docs" element={<DocsPage />} />
    <Route path="pm/docs/:id" element={<DocsPage />} />
    <Route path="pm/tokens" element={<TokensPage />} />
    <Route path="pm/:key" element={<TaskPage />} />
  </>
)

function SignedInRoutes() {
  const { can } = useAuth()
  const me = useAdminMe()

  // Until `me` answers, which panel she gets is unknown: rendering the full one first
  // flashed every page, and its requests, at an operator the server will only show her
  // account page.
  if (me.isLoading) return <Loading rows={3} />

  // Where 2FA is mandatory, an operator who has not enrolled reaches her account page
  // and nothing else; the server refuses the rest. The same holds while she is on the
  // temporary password the Owner gave her. Fixing either refetches `me` and the whole
  // panel opens.
  if (me.data?.totpSetupRequired || me.data?.passwordChangeRequired) {
    return (
      <Routes>
        <Route element={<Shell setupOnly />}>
          <Route path="security" element={<SecurityPage />} />
          <Route path="*" element={<Navigate to="/security" replace />} />
        </Route>
      </Routes>
    )
  }

  // A developer's panel is the Loyiha section and her own account; everything else is about
  // the app's users, which the server refuses her anyway.
  if (me.data?.role === 'DEVELOPER') {
    return (
      <Routes>
        <Route element={<Shell />}>
          {pmRoutes}
          <Route path="security" element={<SecurityPage />} />
          <Route path="*" element={<Navigate to="/pm" replace />} />
        </Route>
      </Routes>
    )
  }

  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<DashboardPage />} />
        {can(PM_ROLES) && pmRoutes}
        <Route
          path="analytics"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <AnalyticsPage /> : <Navigate to="/" replace />}
        />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:id" element={<UserCardPage />} />
        <Route
          path="doctors"
          element={can(['OWNER', 'ADMIN', 'SUPPORT']) ? <DoctorsPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="consultations"
          element={can(['OWNER', 'ADMIN', 'SUPPORT']) ? <ConsultationsPage /> : <Navigate to="/" replace />}
        />
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
        {/* Every role: Support reads the pet's and the frames' prices; the Gul catalogue inside is not hers. */}
        <Route path="shop" element={<ShopPage />} />
        <Route path="promo-video" element={<PromoVideoPage />} />
        <Route
          path="features"
          element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <FeaturesPage /> : <Navigate to="/" replace />}
        />
        {/* The flags page became part of Funksiyalar; an old bookmark lands there. */}
        <Route path="flags" element={<Navigate to="/features" replace />} />
        <Route path="security" element={<SecurityPage />} />
        <Route path="audit" element={can(['OWNER']) ? <AuditPage /> : <Navigate to="/" replace />} />
        <Route path="operators" element={can(['OWNER']) ? <OperatorsPage /> : <Navigate to="/" replace />} />
        <Route path="system" element={can(['OWNER', 'ADMIN']) ? <SystemPage /> : <Navigate to="/" replace />} />
        <Route path="campaigns" element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <CampaignsPage /> : <Navigate to="/" replace />} />
        <Route path="app-release" element={can(['OWNER', 'ADMIN', 'ANALYST']) ? <AppReleasePage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
