import type { AdminOperator, AdminRole } from '../api/types'

/*
 * The words of the operators page, kept out of it so they can be tested. The server holds
 * every rule — Owner only, and never on her own account — and the page just does not
 * offer what it would refuse.
 */

export const roleOrder: AdminRole[] = ['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST']

export const roleLabels: Record<AdminRole, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  SUPPORT: 'Support',
  ANALYST: 'Analitik',
}

/** What each role opens, in one line, for the role picker. */
export const roleHints: Record<AdminRole, string> = {
  OWNER: 'Hamma narsa, shu jumladan operatorlar va audit jurnali',
  ADMIN: "Sozlamalar, pul va moderatsiya — operatorlar va auditsiz",
  SUPPORT: 'Foydalanuvchi kartochkasi, shifokorlar, chat — sozlamalarsiz',
  ANALYST: "Faqat o'qish: analitika, hisobotlar va sozlamalarni ko'rish",
}

export interface OperatorStatus {
  text: string
  tone: 'ok' | 'warn' | 'danger' | 'free'
}

/** One badge for an account, the most pressing state first. */
export function operatorStatus(operator: AdminOperator, now: Date = new Date()): OperatorStatus {
  if (!operator.active) return { text: "O'chirilgan", tone: 'free' }
  if (operator.lockedUntil && Date.parse(operator.lockedUntil) > now.getTime()) {
    return { text: 'Vaqtincha bloklangan', tone: 'danger' }
  }
  if (operator.passwordChangeRequired) return { text: 'Parolini almashtirmagan', tone: 'warn' }
  return { text: 'Faol', tone: 'ok' }
}

/** The page's own check of an email before sending it, so a typo is caught in the form. */
export function looksLikeEmail(value: string): boolean {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value.trim())
}
