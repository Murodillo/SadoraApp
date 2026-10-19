import type { Campaign, CampaignLanguage, CampaignSegment, CampaignText, Language, LifeStage } from '../api/types'

/*
 * The words and small rules of the campaigns page. The server holds the real ones — who is
 * left out and why, the length limits — and this file mirrors them so the form can say so
 * before a request is sent.
 */

export const TITLE_MAX = 65
export const BODY_MAX = 240

export const campaignLanguages: { key: CampaignLanguage; label: string }[] = [
  { key: 'uz', label: "O'zbekcha" },
  { key: 'ru', label: 'Ruscha' },
  { key: 'en', label: 'Inglizcha' },
]

export const lifeStageOptions: { key: LifeStage; label: string }[] = [
  { key: 'cycle', label: 'Sikl' },
  { key: 'trying_to_conceive', label: 'Rejalashtirish' },
  { key: 'pregnancy', label: 'Homiladorlik' },
  { key: 'postpartum', label: "Tug'ruqdan keyin" },
  { key: 'perimenopause', label: 'Perimenopauza' },
  { key: 'menopause', label: 'Menopauza' },
]

export const languageOptions: { key: Language; label: string }[] = [
  { key: 'uz', label: "O'zbekcha" },
  { key: 'ru', label: 'Ruscha' },
  { key: 'en', label: 'Inglizcha' },
]

export const tierOptions: { key: CampaignSegment['tier']; label: string }[] = [
  { key: 'all', label: 'Hamma' },
  { key: 'free', label: 'Bepul' },
  { key: 'premium', label: 'Premium' },
]

export const kindOptions: { key: 'self' | 'partner'; label: string }[] = [
  { key: 'self', label: 'Ayollar' },
  { key: 'partner', label: 'Yaqinimlar' },
]

export const emptySegment: CampaignSegment = { lifeStages: [], languages: [], tier: 'all', accountKinds: ['self'] }

export const skipLabels: Record<string, string> = {
  notifications_off: "Bildirishnomalarni o'chirgan",
  news_off: "Yangiliklarni o'chirgan",
  discreet: 'Yashirin rejimda',
  no_device: "Telefoni ro'yxatda yo'q",
  daily_cap: 'Kunlik chegara tugagan',
  weekly_cap: 'Haftalik chegara tugagan',
}

export const statusLabels: Record<Campaign['status'], { text: string; tone: string }> = {
  scheduled: { text: 'Rejalashtirilgan', tone: 'warn' },
  sending: { text: 'Yuborilmoqda', tone: 'premium' },
  sent: { text: 'Yuborilgan', tone: 'ok' },
  cancelled: { text: 'Bekor qilingan', tone: 'free' },
}

export const testFailures: Record<string, string> = {
  no_account: 'Bu raqam bilan hisob topilmadi',
  no_device: "Bu hisobda push oladigan telefon yo'q",
}

/** Adds or removes one value of a multi-select, keeping the order the options are listed in. */
export function toggle<T>(values: T[], value: T, order: T[]): T[] {
  const next = values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
  return order.filter((item) => next.includes(item))
}

/** "Sikl, Homiladorlik · O'zbekcha · Premium" — the segment as one line; empty parts are "hamma". */
export function segmentSummary(segment: CampaignSegment): string {
  const stages = segment.lifeStages.length
    ? segment.lifeStages.map((key) => lifeStageOptions.find((option) => option.key === key)?.label ?? key).join(', ')
    : 'Barcha bosqichlar'
  const languages = segment.languages.length
    ? segment.languages.map((key) => languageOptions.find((option) => option.key === key)?.label ?? key).join(', ')
    : 'Barcha tillar'
  const tier = tierOptions.find((option) => option.key === segment.tier)?.label ?? segment.tier
  const kinds = segment.accountKinds.map((key) => kindOptions.find((option) => option.key === key)?.label ?? key).join(' va ')
  return [kinds, stages, languages, tier === 'Hamma' ? 'Hamma tariflar' : tier].join(' · ')
}

/** The form's own check before sending: Uzbek complete, every started language complete, all within limits. */
export function textProblems(texts: Partial<Record<CampaignLanguage, CampaignText>>): string[] {
  const problems: string[] = []
  const uz = texts.uz
  if (!uz?.title.trim() || !uz.body.trim()) problems.push("O'zbekcha sarlavha va matn kerak")
  for (const { key, label } of campaignLanguages) {
    const text = texts[key]
    if (!text) continue
    const title = text.title.trim()
    const body = text.body.trim()
    if ((title && !body) || (!title && body)) problems.push(`${label}: sarlavha va matn birga yozilsin`)
    if (title.length > TITLE_MAX) problems.push(`${label}: sarlavha ${TITLE_MAX} belgidan oshmasin`)
    if (body.length > BODY_MAX) problems.push(`${label}: matn ${BODY_MAX} belgidan oshmasin`)
  }
  return problems
}

/** Only the languages actually written go to the server; the others fall back to Uzbek there. */
export function writtenTexts(texts: Partial<Record<CampaignLanguage, CampaignText>>): Partial<Record<CampaignLanguage, CampaignText>> {
  const out: Partial<Record<CampaignLanguage, CampaignText>> = {}
  for (const { key } of campaignLanguages) {
    const text = texts[key]
    if (text && (text.title.trim() || text.body.trim())) out[key] = { title: text.title.trim(), body: text.body.trim() }
  }
  return out
}

/** A `<input type="datetime-local">` value, which has no zone, read as the operator's own time. */
export function localInputToIso(value: string): string | null {
  if (!value) return null
  const at = new Date(value)
  return Number.isNaN(at.getTime()) ? null : at.toISOString()
}

export function sumValues(record: Record<string, number> | undefined): number {
  return Object.values(record ?? {}).reduce((sum, value) => sum + value, 0)
}
