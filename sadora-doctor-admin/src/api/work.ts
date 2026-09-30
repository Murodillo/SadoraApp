import type { DoctorHours, QuickReply } from './types'

/*
 * The small sums behind the doctor's workplace pages: money in tiyin shown as so'm, her
 * hours as minutes from midnight, and the quick replies the composer offers.
 */

/** Her price is 0 (free) or between these, in so'm: `ConsultationService.MIN/MAX_PRICE_MINOR` / 100. */
export const PRICE_MIN_SOM = 1_000
export const PRICE_MAX_SOM = 2_000_000

/** `ConsultationService.MAX_QUICK_REPLIES`, the title's 60 and `MessagingService.SUMMARY_MAX`. */
export const QUICK_REPLIES_MAX = 30
export const QUICK_REPLY_TITLE_MAX = 60
export const NOTE_MAX = 4000
export const SUMMARY_MAX = 2000

/** `1234567` -> `1 234 567`, with plain spaces — the way the app writes sums. */
export function groupDigits(value: number): string {
  return String(Math.trunc(Math.abs(value))).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
}

/**
 * Tiyin as so'm: `5000000` -> `50 000 so'm`. A commission can leave tiyin over; only
 * then are they written, after a comma.
 */
export function formatSom(minor: number | null | undefined): string {
  const value = Math.round(minor ?? 0)
  const sign = value < 0 ? '−' : ''
  const whole = Math.trunc(Math.abs(value) / 100)
  const rest = Math.abs(value) % 100
  return `${sign}${groupDigits(whole)}${rest ? `,${String(rest).padStart(2, '0')}` : ''} so'm`
}

/** What she typed into the price box, in so'm: digits and spaces only, or null. */
export function parseSom(text: string): number | null {
  const digits = text.replace(/[\s ]/g, '')
  if (!/^\d{1,9}$/.test(digits)) return null
  return Number(digits)
}

/** Why a price in so'm cannot be saved, in the server's words, or null. */
export function priceProblem(som: number | null): string | null {
  if (som === null) return 'Faqat raqam kiriting'
  if (som === 0) return null
  if (som < PRICE_MIN_SOM || som > PRICE_MAX_SOM) {
    return `Narx 0 (bepul) yoki ${groupDigits(PRICE_MIN_SOM)}–${groupDigits(PRICE_MAX_SOM)} so'm oralig'ida bo'lsin`
  }
  return null
}

export const weekdayLabels = ['Dushanba', 'Seshanba', 'Chorshanba', 'Payshanba', 'Juma', 'Shanba', 'Yakshanba'] as const

/** A day she switches on starts with these. */
export const DEFAULT_START = 9 * 60
export const DEFAULT_END = 18 * 60

/** `540` -> `09:00`. The end of the day, 1440, is `24:00`. */
export function minutesToTime(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return `${String(hours).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

/** `09:30` -> `570`, or null for anything that is not a time. */
export function timeToMinutes(text: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(text.trim())
  if (!match) return null
  const hours = Number(match[1])
  const minutes = Number(match[2])
  if (hours > 24 || minutes > 59 || (hours === 24 && minutes > 0)) return null
  return hours * 60 + minutes
}

/**
 * A time input cannot hold `24:00`, so a day that runs to midnight shows its end as
 * `00:00`, and an end of `00:00` is read back as midnight at the end of the day.
 */
export function endToInput(minutes: number): string {
  return minutes >= 1440 ? '00:00' : minutesToTime(minutes)
}

export function endFromInput(text: string): number | null {
  const minutes = timeToMinutes(text)
  if (minutes === null) return null
  return minutes === 0 ? 1440 : minutes
}

/** Why a day's hours are wrong, or null. */
export function hoursProblem(day: DoctorHours): string | null {
  if (day.startMinute < 0 || day.startMinute > 1439) return "Boshlanish vaqti noto'g'ri"
  if (day.endMinute <= day.startMinute) return "Boshlanish tugashdan oldin bo'lsin"
  return null
}

/** `9:00–18:00` for a day, the way her week is summed up. */
export function hoursLabel(day: DoctorHours): string {
  return `${minutesToTime(day.startMinute)}–${minutesToTime(day.endMinute)}`
}

/** `12 daqiqa`, `1 soat 5 daqiqa`, `2 kun 3 soat`, or `—` with nothing to say. */
export function durationLabel(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined || Number.isNaN(minutes)) return '—'
  const total = Math.max(0, Math.round(minutes))
  if (total < 1) return '1 daqiqadan kam'
  if (total < 60) return `${total} daqiqa`
  const hours = Math.floor(total / 60)
  if (hours < 24) {
    const rest = total % 60
    return rest ? `${hours} soat ${rest} daqiqa` : `${hours} soat`
  }
  const days = Math.floor(hours / 24)
  const restHours = hours % 24
  return restHours ? `${days} kun ${restHours} soat` : `${days} kun`
}

/** A rating as the app shows it: `4.8`. */
export function ratingLabel(rating: number | null | undefined): string {
  return rating === null || rating === undefined ? '—' : rating.toFixed(1)
}

/**
 * The quick-reply search that "/" at the start of the box opens: `/bel` -> `bel`. Only
 * the whole box counts, and only on one line, so a slash in the middle of a sentence is
 * just a slash.
 */
export function slashQuery(draft: string): string | null {
  const match = /^\/([^\n]{0,40})$/.exec(draft)
  return match ? match[1]! : null
}

/** Her replies whose title or text holds the query, titles that start with it first. */
export function matchQuickReplies(replies: QuickReply[], query: string): QuickReply[] {
  const needle = query.trim().toLocaleLowerCase()
  const ordered = [...replies].sort((a, b) => a.position - b.position || a.title.localeCompare(b.title))
  if (!needle) return ordered
  const starts: QuickReply[] = []
  const contains: QuickReply[] = []
  for (const reply of ordered) {
    const title = reply.title.toLocaleLowerCase()
    if (title.startsWith(needle)) starts.push(reply)
    else if (title.includes(needle) || reply.body.toLocaleLowerCase().includes(needle)) contains.push(reply)
  }
  return [...starts, ...contains]
}

/**
 * The box after a reply is put in: in place of a "/" search, or at the caret with a
 * space or a new line kept between it and what was already written.
 */
export function insertReply(draft: string, caret: number, body: string): { text: string; caret: number } {
  if (slashQuery(draft) !== null) return { text: body, caret: body.length }
  const at = Math.max(0, Math.min(caret, draft.length))
  const before = draft.slice(0, at)
  const after = draft.slice(at)
  const lead = before && !/\s$/.test(before) ? ' ' : ''
  const tail = after && !/^\s/.test(after) ? ' ' : ''
  const text = `${before}${lead}${body}${tail}${after}`
  return { text, caret: before.length + lead.length + body.length }
}
