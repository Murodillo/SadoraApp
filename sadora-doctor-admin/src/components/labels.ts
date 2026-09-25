import type { CommunityTopic, DoctorSpecialty, DoctorStatus } from '../api/types'

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
