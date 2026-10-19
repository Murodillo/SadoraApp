import type { ServiceHealth } from '../api/types'

/*
 * The words of the system health page. The server decides each service's status from what
 * it counted; this file only names things, and says how long ago they happened.
 */

export interface ServiceCopy {
  name: string
  /** What one good outcome is called today; null where nothing is counted. */
  okLabel: string | null
  /** What a failure is called; null where the service never reports one. */
  failLabel: string | null
  lastOkLabel: string
}

const copy: Record<string, ServiceCopy> = {
  sms: { name: 'SMS · Play Mobile', okLabel: 'bugun yuborildi', failLabel: 'rad etildi', lastOkLabel: 'oxirgi SMS' },
  push: { name: 'Push · Firebase', okLabel: 'bugun yetkazildi', failLabel: 'yetmadi', lastOkLabel: 'oxirgi push' },
  ai: { name: 'Gemini AI', okLabel: 'bugun javob berdi', failLabel: 'xato', lastOkLabel: 'oxirgi javob' },
  telegram: { name: 'Telegram bot', okLabel: null, failLabel: 'ulanish xatosi', lastOkLabel: 'oxirgi aloqa' },
  payme: { name: 'Payme', okLabel: "bugun to'lov", failLabel: null, lastOkLabel: "oxirgi to'lov" },
  click: { name: 'Click', okLabel: "bugun to'lov", failLabel: null, lastOkLabel: "oxirgi to'lov" },
  google_play: { name: 'Google Play', okLabel: "bugun to'lov", failLabel: null, lastOkLabel: "oxirgi to'lov" },
  app_store: { name: 'App Store', okLabel: "bugun to'lov", failLabel: null, lastOkLabel: "oxirgi to'lov" },
}

export function serviceCopy(key: string): ServiceCopy {
  return copy[key] ?? { name: key, okLabel: 'bugun', failLabel: 'xato', lastOkLabel: 'oxirgi muvaffaqiyat' }
}

export const statusLabels: Record<ServiceHealth['status'], { text: string; tone: string }> = {
  ok: { text: 'Ishlayapti', tone: 'ok' },
  warn: { text: "Bugun xato bo'lgan", tone: 'warn' },
  error: { text: 'Xato', tone: 'danger' },
  off: { text: 'Sozlanmagan', tone: 'free' },
}

/** The worst first, so a broken service is the first card on the page. */
const severity: Record<ServiceHealth['status'], number> = { error: 0, warn: 1, ok: 2, off: 3 }

export function sortServices(services: ServiceHealth[]): ServiceHealth[] {
  return [...services].sort((a, b) => severity[a.status] - severity[b.status])
}

/** "hozirgina", "5 daq oldin", "3 soat oldin", "2 kun oldin"; "hali yo'q" for never. */
export function ago(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return "hali yo'q"
  const then = Date.parse(iso)
  if (Number.isNaN(then)) return "hali yo'q"
  const minutes = Math.floor((now.getTime() - then) / 60_000)
  if (minutes < 1) return 'hozirgina'
  if (minutes < 60) return `${minutes} daq oldin`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} soat oldin`
  return `${Math.floor(hours / 24)} kun oldin`
}

/** Uptime as "3 kun 4 soat" / "2 soat 15 daq" / "12 daq". */
export function uptime(startedAt: string, now: Date = new Date()): string {
  const minutes = Math.max(0, Math.floor((now.getTime() - Date.parse(startedAt)) / 60_000))
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const rest = minutes % 60
  if (days > 0) return `${days} kun ${hours} soat`
  if (hours > 0) return `${hours} soat ${rest} daq`
  return `${rest} daq`
}
