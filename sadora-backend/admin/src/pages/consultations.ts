import type {
  AdminDoctorQuality,
  AdminRole,
  ConsultationPayment,
  PaymentProvider,
} from '../api/types'

/*
 * The rules and formatting of paid consultations, kept out of the pages so they can be
 * tested. The server enforces every one of them: reading is Owner, Admin and Support;
 * refunds and payouts are Owner and Admin; the commission is the Owner's alone.
 */

/** The three states the admin list filters by, in the order an operator works them. */
export const consultationTabs: ConsultationPayment[] = ['refund_due', 'paid', 'refunded']

export const paymentLabels: Record<ConsultationPayment, { text: string; tone: string }> = {
  free: { text: 'Bepul', tone: 'free' },
  pending: { text: 'Kutilmoqda', tone: 'free' },
  paid: { text: "To'langan", tone: 'ok' },
  refund_due: { text: 'Qaytarilishi kerak', tone: 'warn' },
  refunded: { text: 'Qaytarilgan', tone: 'free' },
}

export const closedReasonLabels: Record<string, string> = {
  doctor: 'Shifokor yopdi',
  expired: '24 soat tugadi',
  refund: 'Javobsiz — qaytarish',
}

export const providerLabels: Record<PaymentProvider, string> = {
  payme: 'Payme',
  click: 'Click',
  app_store: 'App Store',
  google_play: 'Google Play',
}

const moneyWriters: AdminRole[] = ['OWNER', 'ADMIN']

/** Refunds and payouts: Owner and Admin. */
export function canManageMoney(role: AdminRole | undefined): boolean {
  return Boolean(role && moneyWriters.includes(role))
}

/** Sadora's share changes every doctor's income; only the Owner sets it. */
export function canEditCommission(role: AdminRole | undefined): boolean {
  return role === 'OWNER'
}

/** The server takes a refund mark only on money it still holds. */
export function canMarkRefunded(payment: ConsultationPayment, role: AdminRole | undefined): boolean {
  return canManageMoney(role) && (payment === 'paid' || payment === 'refund_due')
}

/**
 * Tiyin to "50 000 so'm". The group separator is a plain space, like the rest of the
 * panel; tiyin show only when there are some, which a commission share can leave.
 */
export function formatSom(minor: number | null | undefined): string {
  if (minor === null || minor === undefined || !Number.isFinite(minor)) return '—'
  const negative = minor < 0
  const abs = Math.abs(Math.round(minor))
  const whole = Math.floor(abs / 100)
  const tiyin = abs % 100
  const grouped = String(whole).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
  const fraction = tiyin ? `,${String(tiyin).padStart(2, '0')}` : ''
  return `${negative ? '−' : ''}${grouped}${fraction} so'm`
}

/**
 * What an operator types into "Summa (so'm)" to tiyin, or null when it is not a positive
 * amount. Spaces group thousands; a comma or a dot starts at most two tiyin digits.
 */
export function parseSomToMinor(text: string): number | null {
  const cleaned = text.replace(/[\s  ]/g, '')
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(cleaned)
  if (!match) return null
  const minor = Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'))
  if (!Number.isSafeInteger(minor) || minor <= 0) return null
  return minor
}

/** The commission input: a whole percent from 0 to 100, or null. */
export function parsePercent(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d{1,3}$/.test(trimmed)) return null
  const value = Number(trimmed)
  return value >= 0 && value <= 100 ? value : null
}

/** From the window opening to her first line in it, in whole minutes. */
export function firstReplyMinutes(openedAt?: string | null, firstReplyAt?: string | null): number | null {
  if (!openedAt || !firstReplyAt) return null
  const opened = Date.parse(openedAt)
  const replied = Date.parse(firstReplyAt)
  if (Number.isNaN(opened) || Number.isNaN(replied) || replied < opened) return null
  return Math.round((replied - opened) / 60_000)
}

/** `75` -> `1 soat 15 daq`. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined || !Number.isFinite(minutes)) return '—'
  if (minutes < 60) return `${minutes} daq`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} soat ${rest} daq` : `${hours} soat`
}

// ---------------------------------------------------------------- quality table sorting

export type QualitySortKey =
  | 'fullName'
  | 'priceMinor'
  | 'consultationsMonth'
  | 'consultationsTotal'
  | 'openNow'
  | 'avgFirstReplyMinutes'
  | 'unansweredTotal'
  | 'rating'
  | 'grossMinor'
  | 'netMinor'
  | 'paidOutMinor'
  | 'balanceMinor'
  | 'refundDueMinor'

export interface QualitySort {
  key: QualitySortKey
  descending: boolean
}

/** The direction a column starts in: names A–Z, reply time fastest first, the rest most first. */
export function defaultDescending(key: QualitySortKey): boolean {
  return key !== 'fullName' && key !== 'avgFirstReplyMinutes'
}

/** Clicking the sorted column flips it; clicking another starts that one in its own direction. */
export function nextSort(current: QualitySort, key: QualitySortKey): QualitySort {
  return current.key === key ? { key, descending: !current.descending } : { key, descending: defaultDescending(key) }
}

/**
 * A new array, never the query cache's. A doctor with no rating or no answered window
 * sorts last in either direction: "no data" is not the best or the worst score.
 */
export function sortQuality(rows: AdminDoctorQuality[], sort: QualitySort): AdminDoctorQuality[] {
  const direction = sort.descending ? -1 : 1
  return [...rows].sort((a, b) => {
    const left = a[sort.key]
    const right = b[sort.key]
    const leftMissing = left === null || left === undefined
    const rightMissing = right === null || right === undefined
    if (leftMissing || rightMissing) {
      if (leftMissing && rightMissing) return a.fullName.localeCompare(b.fullName)
      return leftMissing ? 1 : -1
    }
    const order =
      typeof left === 'string' && typeof right === 'string'
        ? left.localeCompare(right)
        : (left as number) - (right as number)
    return order === 0 ? a.fullName.localeCompare(b.fullName) : order * direction
  })
}
