import type { AdminRole, ModerationReport, ReportContextMessage, ReportContextView, ReportReason } from '../api/types'

/*
 * The rules of a reported private message, kept out of the page so they can be tested.
 * A private thread is never browsable from the panel: the moderator sees the reported
 * line and the few around it, only through a report, and the server audits each look.
 * These mirror the server, which is where they are enforced.
 */

export const reasonLabels: Record<ReportReason, string> = {
  spam: 'Spam',
  abuse: 'Haqorat',
  misinformation: 'Xavfli maslahat',
  personal_data: "Shaxsiy ma'lumot",
  other: 'Boshqa',
}

/** Reading the lines around a report; Analyst reads the queue but never a thread. */
export const contextRoles: AdminRole[] = ['OWNER', 'ADMIN', 'SUPPORT']

export function canViewMessageContext(role: AdminRole | undefined): boolean {
  return Boolean(role && contextRoles.includes(role))
}

export const PRIVACY_LINE = "Shaxsiy yozishmalar faqat shikoyat bo'yicha ko'rinadi. Har ochilish audit jurnaliga yoziladi."

export const RECORD_LINE = "📋 Tibbiy karta biriktirilgan (mazmuni ko'rsatilmaydi)"

export const IMAGE_PLACEHOLDER = '[rasm]'

/** What was reported, as the queue names it. */
export function reportTarget(report: Pick<ModerationReport, 'messageId' | 'commentId' | 'consultation'>): {
  text: string
  tone: string
} {
  if (report.messageId) {
    return report.consultation ? { text: 'Konsultatsiya', tone: 'consult' } : { text: 'Shaxsiy xabar', tone: 'premium' }
  }
  return report.commentId ? { text: 'izoh', tone: '' } : { text: 'post', tone: '' }
}

export function conversationLabel(view: Pick<ReportContextView, 'consultation'>): string {
  return view.consultation ? 'Konsultatsiya' : 'Shaxsiy xabar'
}

/**
 * The line's text as the moderator may read it. A record's body is empty on the wire and
 * would stay unshown anyway; a photo is a placeholder, with its caption if it had one.
 */
export function contextLineText(line: Pick<ReportContextMessage, 'kind' | 'body'>): string {
  switch (line.kind) {
    case 'record':
      return RECORD_LINE
    case 'image':
      return line.body.trim() ? `${IMAGE_PLACEHOLDER} ${line.body}` : IMAGE_PLACEHOLDER
    default:
      return line.body
  }
}

/** Who wrote the line, by the labels the server gave — an alias, or a doctor's name. */
export function contextLineAuthor(view: Pick<ReportContextView, 'reporter' | 'reported'>, line: Pick<ReportContextMessage, 'fromReported'>): string {
  return line.fromReported ? view.reported : view.reporter
}

/**
 * Whether this line was written by the doctor. In a consultation one side is always the
 * doctor, so if the reported side is not, the reporter is. The server's label already
 * carries the ✓; this only decides the bubble's colour.
 */
export function isDoctorLine(
  view: Pick<ReportContextView, 'consultation' | 'reportedIsDoctor'>,
  line: Pick<ReportContextMessage, 'fromReported'>,
): boolean {
  if (!view.consultation) return false
  return line.fromReported ? view.reportedIsDoctor : !view.reportedIsDoctor
}

/** Only the reported photo can be opened — the server has no route to any other. */
export function canOpenImage(line: Pick<ReportContextMessage, 'kind' | 'reported'>): boolean {
  return line.reported && line.kind === 'image'
}

export interface RestrictDuration {
  value: string
  label: string
  /** Null lasts until a moderator lifts it. */
  days: number | null
}

export const restrictDurations: RestrictDuration[] = [
  { value: '1', label: '1 kun', days: 1 },
  { value: '7', label: '7 kun', days: 7 },
  { value: '30', label: '30 kun', days: 30 },
  { value: 'forever', label: 'Cheksiz', days: null },
]

export function restrictDone(days: number | null): string {
  return days ? `Yuboruvchi ${days} kunga cheklandi` : 'Yuboruvchi muddatsiz cheklandi'
}
