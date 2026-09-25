import type { AdminRole, DoctorDocumentKind, DoctorReviewAction, DoctorSpecialty, DoctorStatus } from '../api/types'
import { limits } from '../api/limits'

/*
 * The rules of the doctor review queue, kept out of the page so they can be tested.
 * They mirror the server, which is where they are enforced: approve and reject only a
 * pending application, suspend only an approved doctor, reinstate only a suspended one,
 * and only Owner and Admin may do any of it.
 */

export const doctorStatusOrder: DoctorStatus[] = ['pending', 'approved', 'rejected', 'suspended']

export const doctorStatusLabels: Record<DoctorStatus, { text: string; tone: string }> = {
  pending: { text: 'Kutilmoqda', tone: 'warn' },
  approved: { text: 'Tasdiqlangan', tone: 'ok' },
  rejected: { text: 'Rad etilgan', tone: 'danger' },
  suspended: { text: "To'xtatilgan", tone: 'free' },
}

export const specialtyLabels: Record<DoctorSpecialty, string> = {
  gynecologist: 'Ginekolog',
  obstetrician: 'Akusher',
  reproductologist: 'Reproduktolog',
  endocrinologist: 'Endokrinolog',
  mammologist: 'Mammolog',
  psychologist: 'Psixolog',
  nutritionist: 'Nutritsiolog',
  pediatrician: 'Pediatr',
  general: 'Umumiy amaliyot shifokori',
  other: 'Boshqa',
}

export const documentKindLabels: Record<DoctorDocumentKind, string> = {
  diploma: 'Diplom',
  license: 'Litsenziya',
  other: 'Boshqa',
}

export const reviewActionLabels: Record<DoctorReviewAction, string> = {
  approve: 'Tasdiqlash',
  reject: 'Rad etish',
  suspend: "To'xtatish",
  reinstate: 'Qayta tiklash',
}

/** What the operator is told once the server has accepted the action. */
export const reviewActionDone: Record<DoctorReviewAction, string> = {
  approve: 'Shifokor tasdiqlandi',
  reject: 'Ariza rad etildi',
  suspend: "Shifokor to'xtatildi",
  reinstate: 'Shifokor qayta tiklandi',
}

const transitions: Record<DoctorStatus, DoctorReviewAction[]> = {
  pending: ['approve', 'reject'],
  approved: ['suspend'],
  rejected: [],
  suspended: ['reinstate'],
}

const reviewers: AdminRole[] = ['OWNER', 'ADMIN']

/** The buttons a given operator sees for a doctor in a given state. Support sees none. */
export function allowedReviewActions(status: DoctorStatus, role: AdminRole | undefined): DoctorReviewAction[] {
  if (!role || !reviewers.includes(role)) return []
  return transitions[status] ?? []
}

/** Reject and suspend tell the doctor why; the server refuses them without a note. */
export function reviewNeedsNote(action: DoctorReviewAction): boolean {
  return action === 'reject' || action === 'suspend'
}

/** Whether a note is good enough to send for this action. */
export function reviewNoteValid(action: DoctorReviewAction, note: string): boolean {
  const trimmed = note.trim()
  if (trimmed.length > limits.reasonMax) return false
  return reviewNeedsNote(action) ? trimmed.length > 0 : true
}

/** `1536` -> `1.5 KB`: the size under a document thumbnail. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
