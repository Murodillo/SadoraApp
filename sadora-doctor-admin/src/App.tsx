import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { createQueryClient } from './api/queryClient'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { LoginPage } from './auth/LoginPage'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ToastProvider } from './components/toast'
import { DoctorGate } from './pages/DoctorGate'

const queryClient = createQueryClient()

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
 * Signed out: the phone sign-in. Signed in: the gate, which reads her doctor account and
 * shows either the workspace or where her application stands.
 */
export function AppRoutes() {
  const { session } = useAuth()
  if (!session) return <LoginPage />
  return <DoctorGate />
}
