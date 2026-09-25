import { createContext, useContext } from 'react'
import type { DoctorAccount, DoctorStatus } from '../api/types'

/** What the panel shows for an account: the workspace, or a page about her application. */
export type Screen = 'workspace' | 'status'

/**
 * Only an approved doctor gets the workspace. Every other status — and one the server
 * adds later that this build has never heard of — gets the status page, because the
 * server would refuse the workspace's calls anyway.
 */
export function screenFor(status: DoctorStatus | string): Screen {
  return status === 'approved' ? 'workspace' : 'status'
}

/** The signed-in doctor, set by the gate once her account is known to be approved. */
export const ApprovedDoctorContext = createContext<DoctorAccount | null>(null)

export function useApprovedDoctor(): DoctorAccount {
  const value = useContext(ApprovedDoctorContext)
  if (!value) throw new Error('useApprovedDoctor must be used inside the approved workspace')
  return value
}
