import type { CommunityTopic, ConsultationPayment, DoctorSpecialty, DoctorStatus, LifeStage, ReportReason } from '../api/types'

/*
 * The words the app uses, so a doctor reads the same name for a room here as the woman
 * who asked in it. Specialties come from `DoctorStringsUz.specialty` and topics from
 * `StringsUz.topic` in sadora-client; change them there and change them here.
 */

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
  other: 'Boshqa mutaxassis',
}

/** A specialty the server adds later is shown as it came rather than as nothing. */
export function specialtyLabel(specialty: DoctorSpecialty | null | undefined): string {
  if (!specialty) return '—'
  return specialtyLabels[specialty] ?? specialty
}

export const topicOrder: CommunityTopic[] = ['cycle', 'pregnancy', 'wellbeing', 'body']

export const topicLabels: Record<CommunityTopic, string> = {
  cycle: 'Sikl',
  pregnancy: 'Homiladorlik',
  wellbeing: 'Kayfiyat',
  body: 'Tana',
}

export function topicLabel(topic: CommunityTopic): string {
  return topicLabels[topic] ?? topic
}

export const statusLabels: Record<DoctorStatus, { text: string; tone: string }> = {
  none: { text: "Ariza yo'q", tone: 'free' },
  pending: { text: "Ko'rib chiqilmoqda", tone: 'warn' },
  approved: { text: 'Tasdiqlangan', tone: 'ok' },
  rejected: { text: 'Rad etilgan', tone: 'danger' },
  suspended: { text: "To'xtatilgan", tone: 'danger' },
}

/** The line the app puts under a doctor's answer; the panel shows it under the answer box. */
export const ANSWER_DISCLAIMER = "Chatdagi javobingiz umumiy maslahat sifatida ko'rinadi, tashxis emas."

/** Her stage of life, as the doctor app words it (`TabStringsUz.lifeStage`). */
export const lifeStageLabels: Record<LifeStage, string> = {
  cycle: 'Hayz sikli',
  trying_to_conceive: 'Homiladorlikni rejalashtirmoqda',
  pregnancy: 'Homiladorlik',
  postpartum: "Tug'ruqdan keyingi davr",
  perimenopause: 'Perimenopauza',
  menopause: 'Menopauza',
}

export function lifeStageLabel(stage: LifeStage | null | undefined): string {
  if (!stage) return '—'
  return lifeStageLabels[stage] ?? stage
}

/** Report reasons, as the women's app words them (`StringsUz.reportReason`). */
export const reportReasonOrder: ReportReason[] = ['abuse', 'spam', 'misinformation', 'personal_data', 'other']

export const reportReasonLabels: Record<ReportReason, string> = {
  spam: 'Spam yoki reklama',
  abuse: 'Haqorat yoki tahdid',
  misinformation: 'Xavfli tibbiy maslahat',
  personal_data: "Shaxsiy ma'lumot oshkor qilingan",
  other: 'Boshqa sabab',
}

/** Under the patient's record in the panel. */
export const RECORD_DISCLAIMER = "Bu bemor o'zi kiritgan ma'lumotlar, tashxis emas"

/** Where a consultation's money stands, in a chip. */
export const paymentLabels: Record<ConsultationPayment, { text: string; tone: string }> = {
  free: { text: 'Bepul', tone: 'free' },
  pending: { text: "To'lov kutilmoqda", tone: 'warn' },
  paid: { text: "To'langan", tone: 'ok' },
  refund_due: { text: 'Qaytarilishi kerak', tone: 'danger' },
  refunded: { text: 'Qaytarilgan', tone: 'free' },
}

export function paymentLabel(payment: ConsultationPayment | null | undefined): { text: string; tone: string } {
  return paymentLabels[payment ?? 'free'] ?? { text: String(payment), tone: 'free' }
}

/** Why a window ended, in the history of a patient. */
export const closedReasonLabels: Record<string, string> = {
  doctor: 'Siz yakunlagansiz',
  expired: 'Muddati tugagan',
  refund: 'Javobsiz yopilgan',
}
