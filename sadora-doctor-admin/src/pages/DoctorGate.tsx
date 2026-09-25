import { Navigate, Route, Routes } from 'react-router-dom'
import { useDoctorAccount } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { ApprovedDoctorContext, screenFor } from '../auth/doctor'
import { SadoraMark } from '../components/Logo'
import { ErrorNotice } from '../components/ui'
import { Shell } from '../layout/Shell'
import { PostsPage } from './PostsPage'
import { ProfilePage } from './ProfilePage'
import { QuestionsPage } from './QuestionsPage'
import { StatusPage } from './StatusPage'

/**
 * Between sign-in and the workspace: her doctor account decides what she sees. The
 * server enforces the same line on every doctor route; this only spares her a panel of
 * refusals.
 */
export function DoctorGate() {
  const account = useDoctorAccount()
  const { signOut } = useAuth()

  if (account.isPending) {
    return (
      <div className="gate-center" aria-busy="true" aria-label="Yuklanmoqda">
        <SadoraMark size={64} className="gate-mark" />
      </div>
    )
  }

  if (account.isError) {
    return (
      <div className="gate-center">
        <div className="card gate-card">
          <h2 className="gate-title">Hisob ma'lumotlari olinmadi</h2>
          <ErrorNotice error={account.error} />
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button className="btn" type="button" onClick={signOut}>
              Chiqish
            </button>
            <button className="btn primary" type="button" onClick={() => void account.refetch()}>
              Qayta urinish
            </button>
          </div>
        </div>
      </div>
    )
  }

  const doctor = account.data
  if (screenFor(doctor.status) === 'status') {
    return <StatusPage account={doctor} refreshing={account.isFetching} onRefresh={() => void account.refetch()} />
  }

  return (
    <ApprovedDoctorContext.Provider value={doctor}>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<QuestionsPage />} />
          <Route path="posts" element={<PostsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ApprovedDoctorContext.Provider>
  )
}
